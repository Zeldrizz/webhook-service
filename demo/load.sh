#!/usr/bin/env bash
# demo/load.sh — генератор нагрузки для демонстрации Grafana-дашбордов
#
# Первый запуск: создаёт 5 plain + 5 proxy вебхуков, сохраняет в demo/.state
# Повторный запуск: переиспользует те же вебхуки
# Сбросить: rm demo/.state
#
# Использование:   bash demo/load.sh
# Остановить:      Ctrl+C
#
# Переменные окружения (опционально):
#   WEBHOOK_BASE_URL  — по умолчанию http://localhost:8080
#   WEBHOOK_API_KEY   — по умолчанию password
#   PROXY_TARGET      — по умолчанию http://host.docker.internal:9099 (локальный mock)

set -euo pipefail

# конфигурация
BASE_URL="${WEBHOOK_BASE_URL:-http://localhost:8080}"
API_KEY="${WEBHOOK_API_KEY:-password}"
MOCK_PORT="${MOCK_PORT:-9099}"
PROXY_TARGET="${PROXY_TARGET:-http://host.docker.internal:${MOCK_PORT}}"
NUM_PLAIN=5
NUM_PROXY=5
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_FILE="$SCRIPT_DIR/.state"

# цвета
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; DIM='\033[2m'; RESET='\033[0m'

# проверка зависимостей
for cmd in curl jq; do
    command -v "$cmd" &>/dev/null || { echo "Ошибка: $cmd не найден"; exit 1; }
done

# счётчики (нужны до trap)
REQ_COUNT=0
ERR_COUNT=0
MOCK_PID=""

# остановка по Ctrl+C
cleanup() {
    echo -e "\n\n${YELLOW}Остановлено.${RESET}  Запросов: ${BOLD}$REQ_COUNT${RESET}  Ошибок: ${RED}$ERR_COUNT${RESET}"
    [[ -n "$MOCK_PID" ]] && kill "$MOCK_PID" 2>/dev/null || true
    exit 0
}
trap cleanup INT TERM

# создание одного вебхука
create_webhook() {
    local name="$1" proxy_url="$2"
    local body
    if [[ -n "$proxy_url" ]]; then
        body=$(jq -cn --arg n "$name" --arg p "$proxy_url" \
            '{name:$n, methods:"POST,GET", proxyUrl:$p, debugMode:true}')
    else
        body=$(jq -cn --arg n "$name" \
            '{name:$n, methods:"POST,GET", debugMode:true}')
    fi
    curl -sf -X POST "$BASE_URL/api/webhooks" \
        -H "X-API-Key: $API_KEY" \
        -H "Content-Type: application/json" \
        -d "$body"
}

# запуск локального mock-target если используется порт 9099
MOCK_JS="$SCRIPT_DIR/../load-tests/mock-target.js"
if [[ "$PROXY_TARGET" == *"$MOCK_PORT"* ]] && command -v node &>/dev/null && [[ -f "$MOCK_JS" ]]; then
    if ! curl -fsS "http://localhost:${MOCK_PORT}" &>/dev/null; then
        MOCK_PORT="$MOCK_PORT" node "$MOCK_JS" &
        MOCK_PID=$!
        sleep 0.5
        echo -e "  ${DIM}mock-target запущен на :${MOCK_PORT} (pid $MOCK_PID)${RESET}"
    else
        echo -e "  ${DIM}mock-target уже слушает :${MOCK_PORT}${RESET}"
    fi
fi

echo -e "${BOLD}${CYAN}"
echo "  ╔══════════════════════════════════════════╗"
echo "  ║    Webhook Service  —  Demo Load Gen     ║"
echo "  ╚══════════════════════════════════════════╝"
echo -e "${RESET}"
echo -e "  Target : ${BOLD}$BASE_URL${RESET}"
echo -e "  Proxy  : ${DIM}$PROXY_TARGET${RESET}"
echo ""

# загрузка или создание вебхуков
SLUGS=()
TYPES=()

if [[ -f "$STATE_FILE" ]]; then
    echo -e "${YELLOW}  Загружаю существующие вебхуки из demo/.state${RESET}"
    echo -e "  ${DIM}(удали demo/.state чтобы создать новый набор)${RESET}"
    while IFS=$'\t' read -r slug type; do
        [[ -z "$slug" ]] && continue
        SLUGS+=("$slug")
        TYPES+=("$type")
    done < "$STATE_FILE"
    if [[ ${#SLUGS[@]} -eq 0 ]]; then
        echo -e "  ${YELLOW}demo/.state пуст — удаляю и создаю вебхуки заново${RESET}"
        rm -f "$STATE_FILE"
    else
        echo -e "  Загружено: ${BOLD}${#SLUGS[@]}${RESET} вебхуков."
    fi
fi

if [[ ${#SLUGS[@]} -eq 0 ]]; then
    # проверяем доступность сервиса
    if ! curl -sf "$BASE_URL/api/health" &>/dev/null; then
        echo -e "${RED}  Ошибка: сервис недоступен по адресу $BASE_URL${RESET}"
        exit 1
    fi

    echo -e "  Создаю $NUM_PLAIN plain + $NUM_PROXY proxy вебхуков...\n"

    # уникальный суффикс через date+pid, чтобы избежать SIGPIPE с pipefail
    SUFFIX=$(printf '%x' $(( $(date +%s) ^ $$ )) | tail -c 5)

    for i in $(seq 1 $NUM_PLAIN); do
        NAME="demo plain ${SUFFIX}${i}"
        resp=$(create_webhook "$NAME" "")
        slug=$(echo "$resp" | jq -r '.slug')
        SLUGS+=("$slug")
        TYPES+=("plain")
        printf "  ${GREEN}✓ plain${RESET}  /webhook/%s\n" "$slug"
    done

    for i in $(seq 1 $NUM_PROXY); do
        NAME="demo proxy ${SUFFIX}${i}"
        resp=$(create_webhook "$NAME" "$PROXY_TARGET")
        slug=$(echo "$resp" | jq -r '.slug')
        SLUGS+=("$slug")
        TYPES+=("proxy")
        printf "  ${CYAN}✓ proxy${RESET}  /webhook/%s  →  %s\n" "$slug" "$PROXY_TARGET"
    done

    # сохраняем состояние
    printf '' > "$STATE_FILE"
    for i in "${!SLUGS[@]}"; do
        printf '%s\t%s\n' "${SLUGS[$i]}" "${TYPES[$i]}" >> "$STATE_FILE"
    done
    echo -e "\n  Сохранено в demo/.state"
fi

echo ""
echo -e "  ${BOLD}Генерирую нагрузку...  ${YELLOW}Ctrl+C для остановки${RESET}"
echo -e "  ${DIM}plain = прямая обработка  •  proxy = форвардинг на $PROXY_TARGET${RESET}"
echo ""

# пейлоады
PAYLOADS=(
    '{"event":"user.signup","userId":"u-001","email":"alice@example.com","source":"web"}'
    '{"event":"order.placed","orderId":"ord-042","amount":99.95,"currency":"USD","items":3}'
    '{"event":"payment.success","txId":"tx-7891","amount":49.00,"gateway":"stripe"}'
    '{"event":"user.login","userId":"u-002","ip":"10.0.1.42","device":"mobile"}'
    '{"event":"order.cancelled","orderId":"ord-043","reason":"user_request","refund":true}'
    '{"event":"subscription.renewed","userId":"u-003","plan":"pro","cycle":"monthly"}'
    '{"event":"inventory.low","productId":"sku-999","stock":3,"threshold":10}'
    '{"event":"deploy.finished","service":"api","version":"v2.4.1","env":"prod"}'
)

# основной цикл
ROUND=0

while true; do
    ROUND=$((ROUND + 1))

    for i in "${!SLUGS[@]}"; do
        SLUG="${SLUGS[$i]}"
        TYPE="${TYPES[$i]}"
        ENDPOINT="$BASE_URL/webhook/$SLUG"

        # 80% POST, 20% GET
        if (( RANDOM % 5 == 0 )); then
            STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
                -X GET "$ENDPOINT" --max-time 12 2>/dev/null || echo "000")
            METHOD="GET "
        else
            PAYLOAD="${PAYLOADS[$((RANDOM % ${#PAYLOADS[@]}))]}"
            STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
                -X POST "$ENDPOINT" \
                -H "Content-Type: application/json" \
                -d "$PAYLOAD" \
                --max-time 12 2>/dev/null || echo "000")
            METHOD="POST"
        fi

        REQ_COUNT=$((REQ_COUNT + 1))

        if [[ "$STATUS" == "2"* ]]; then
            S_COLOR="$GREEN"; ERR_IND=" "
        else
            ERR_COUNT=$((ERR_COUNT + 1))
            S_COLOR="$RED"; ERR_IND="!"
        fi

        [[ "$TYPE" == "proxy" ]] && T_COLOR="$CYAN" || T_COLOR="$GREEN"
        [[ "$TYPE" == "proxy" ]] && ICON="⇄" || ICON="•"

        printf "\r  ${DIM}round %-4d${RESET}  sent ${BOLD}%-6d${RESET}  err ${RED}%-4d${RESET}  %s ${S_COLOR}%s${RESET}  ${T_COLOR}%s${RESET} %-32s%s " \
            "$ROUND" "$REQ_COUNT" "$ERR_COUNT" "$METHOD" "$STATUS" "$ICON" "$SLUG" "$ERR_IND"

        sleep 0.2
    done

    # пауза между раундами (~1 сек)
    sleep 0.8
done
