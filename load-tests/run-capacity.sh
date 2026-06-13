#!/usr/bin/env bash
#
# Capacity / performance characterization sweep (Demo 6+, Marat).
#
# Goal: find the MAXIMUM SUSTAINABLE RPS and the saturation behaviour of the
# service, swept across a parameter matrix, so we can set SLI/SLO/SLA.
#
# Matrix:   CACHE_ENABLED {on,off} x VERTICLE_INSTANCES {1,2,4}  = 6 configs.
# Per config, per workload {simple, proxy, crud}: an OPEN-MODEL staircase
# (constant-arrival-rate at rising target RPS). Then a soak and a spike on the
# `simple` hot path. Open model = honest capacity (no coordinated omission).
#
#   Usage:  ./run-capacity.sh           # full sweep (~2-3h on a 12-vCPU host)
#           QUICK=1 ./run-capacity.sh    # fast pipeline validation (~15 min)
#
# Everything is env-tunable (see defaults below). Raw per-run JSON -> results/,
# aggregated report -> CAPACITY.md (regenerated at the end via gen-capacity-report.js).
#
# Requires: docker compose, k6, node, curl.
set -uo pipefail   # NOTE: not -e — a single wedged step must not abort the whole sweep.

cd "$(dirname "$0")"
LT_DIR="$(pwd)"
REPO_DIR="$(dirname "$LT_DIR")"
RESULTS_DIR="$LT_DIR/results"
CAP_DIR="$RESULTS_DIR/capacity"

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-password}"
MOCK_PORT="${MOCK_PORT:-9099}"
export BASE_URL API_KEY MOCK_PORT

# matrix axes
CACHE_MODES="${CACHE_MODES:-on off}"
VERTICLE_SET="${VERTICLE_SET:-1 2 4}"
WORKLOADS="${WORKLOADS:-simple proxy crud}"

# staircase step lists (req/s) per workload
STEPS_SIMPLE="${STEPS_SIMPLE:-1000 2000 4000 6000 8000 10000 12000 14000 16000 18000 20000 24000}"
STEPS_PROXY="${STEPS_PROXY:-1000 2000 3000 4000 5000 6000 7000 8000 10000}"
STEPS_CRUD="${STEPS_CRUD:-500 1000 2000 3000 4000 5000 6000 8000}"

# timings
STEP_DURATION="${STEP_DURATION:-25s}"
STEP_COOLDOWN="${STEP_COOLDOWN:-4}"
SOAK_DURATION="${SOAK_DURATION:-5m}"
PREALLOC_VUS="${PREALLOC_VUS:-200}"
MAX_VUS="${MAX_VUS:-3000}"

# SLO latency budgets (ms)
BUDGET_SIMPLE="${BUDGET_SIMPLE:-50}"
BUDGET_PROXY="${BUDGET_PROXY:-100}"
BUDGET_CRUD="${BUDGET_CRUD:-150}"
export BUDGET_SIMPLE BUDGET_PROXY BUDGET_CRUD

if [ "${QUICK:-0}" = "1" ]; then
  echo "==> QUICK mode: trimmed matrix + steps"
  CACHE_MODES="on off"; VERTICLE_SET="1"; WORKLOADS="simple proxy"
  STEPS_SIMPLE="1000 4000 8000"; STEPS_PROXY="1000 3000 5000"; STEPS_CRUD="500 2000"
  STEP_DURATION="10s"; SOAK_DURATION="20s"
fi

mkdir -p "$CAP_DIR"
# Resumable by default: keep existing per-step JSON so a relaunch continues where
# it stopped (a full sweep is long and may be interrupted). CLEAN=1 starts fresh.
if [ "${CLEAN:-0}" = "1" ]; then
  echo "==> CLEAN=1: wiping previous results"
  rm -f "$CAP_DIR"/cap_*.json "$CAP_DIR"/stats_*.txt 2>/dev/null || true
fi

MOCK_PID=""
cleanup() {
  echo "==> Cleanup"
  [ -n "$MOCK_PID" ] && kill "$MOCK_PID" 2>/dev/null || true
  (cd "$REPO_DIR" && docker compose down -v >/dev/null 2>&1) || true
}
trap cleanup EXIT

require() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: '$1' not found in PATH"; exit 1; }; }
require docker; require k6; require node; require curl

wait_for_health() {
  echo -n "==> Waiting for health "
  for _ in $(seq 1 60); do
    curl -fsS "$BASE_URL/api/health" >/dev/null 2>&1 && { echo "OK"; return 0; }
    echo -n "."; sleep 2
  done
  echo "FAILED"; (cd "$REPO_DIR" && docker compose logs app | tail -40); return 1
}

start_stack() { # $1 cache(on|off)  $2 verticles
  local enabled="true"; [ "$1" = "off" ] && enabled="false"
  echo "==> Stack: CACHE_ENABLED=$enabled VERTICLE_INSTANCES=$2"
  (cd "$REPO_DIR" && CACHE_ENABLED="$enabled" VERTICLE_INSTANCES="$2" \
      docker compose up --build -d --force-recreate app db) >/dev/null 2>&1
  wait_for_health || return 1
  curl -fsS -X POST -H "X-API-Key: $API_KEY" "$BASE_URL/api/cache/flush" >/dev/null 2>&1 || true
}

steps_for() { case "$1" in simple) echo "$STEPS_SIMPLE";; proxy) echo "$STEPS_PROXY";; crud) echo "$STEPS_CRUD";; esac; }

run_step() { # $1 workload  $2 cache  $3 verticles  $4 rps  $5 phase  $6 duration
  local wl="$1" cache="$2" v="$3" rps="$4" phase="$5" dur="$6"
  local out="$CAP_DIR/cap_${cache}_v${v}_${wl}_${phase}_${rps}.json"
  if [ -s "$out" ]; then echo "   skip (cached) $wl $phase @$rps"; return 0; fi
  WORKLOAD="$wl" TARGET_RPS="$rps" DURATION="$dur" PHASE="$phase" \
  CACHE_MODE="$cache" VERTICLES="$v" \
  PREALLOC_VUS="$PREALLOC_VUS" MAX_VUS="$MAX_VUS" \
  SUMMARY_OUT="$out" \
    taskset -c 4-11 k6 run --quiet capacity.js >/dev/null 2>&1 || echo "   (k6 non-zero for $wl@$rps — JSON still captured)"
}

snap_stats() { # $1 label
  docker stats --no-stream --format '{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' \
    >"$CAP_DIR/stats_$1.txt" 2>/dev/null || true
}

echo "==> mock-target on :$MOCK_PORT"
MOCK_PORT="$MOCK_PORT" node mock-target.js & MOCK_PID=$!
sleep 1

for cache in $CACHE_MODES; do
  for v in $VERTICLE_SET; do
    start_stack "$cache" "$v" || { echo "   skip config $cache/v$v (unhealthy)"; continue; }
    echo "==> Warm-up"
    for _ in $(seq 1 50); do curl -fsS -H "X-API-Key: $API_KEY" "$BASE_URL/api/webhooks?page=0&size=5" >/dev/null 2>&1 || true; done

    for wl in $WORKLOADS; do
      echo "==> Staircase: cache=$cache v=$v workload=$wl"
      # Throwaway warm-up on the real workload so the first MEASURED step isn't a
      # cold-start outlier (JIT compile + connection-pool fill). Not recorded.
      WORKLOAD="$wl" TARGET_RPS=500 DURATION=8s PHASE=warmup CACHE_MODE="$cache" VERTICLES="$v" \
      PREALLOC_VUS="$PREALLOC_VUS" MAX_VUS="$MAX_VUS" \
        taskset -c 4-11 k6 run --quiet capacity.js >/dev/null 2>&1 || true
      for rps in $(steps_for "$wl"); do
        run_step "$wl" "$cache" "$v" "$rps" "capacity" "$STEP_DURATION"
        sleep "$STEP_COOLDOWN"
      done
    done
    snap_stats "${cache}_v${v}_peak"

    # Soak + spike on the simple hot path, at the measured sustainable RPS for this config.
    SOAK_RPS=$(node compute-sustainable.js "$CAP_DIR" "$cache" "$v" simple "$BUDGET_SIMPLE" 2>/dev/null || echo 0)
    if [ "${SOAK_RPS:-0}" -gt 0 ]; then
      echo "==> Soak: cache=$cache v=$v simple @ ${SOAK_RPS} rps for $SOAK_DURATION"
      snap_stats "${cache}_v${v}_soak_start"
      run_step simple "$cache" "$v" "$SOAK_RPS" "soak" "$SOAK_DURATION"
      snap_stats "${cache}_v${v}_soak_end"

      SPIKE_PEAK=$(( SOAK_RPS * 2 ))
      SPIKE_OUT="$CAP_DIR/cap_${cache}_v${v}_simple_spike_${SPIKE_PEAK}.json"
      if [ -s "$SPIKE_OUT" ]; then
        echo "==> Spike cached (cache=$cache v=$v) — skip"
      else
        echo "==> Spike: cache=$cache v=$v simple base=${SOAK_RPS} peak=${SPIKE_PEAK}"
        WORKLOAD=simple CACHE_MODE="$cache" VERTICLES="$v" \
        SPIKE_BASE_RPS="$SOAK_RPS" SPIKE_PEAK_RPS="$SPIKE_PEAK" TARGET_RPS="$SPIKE_PEAK" \
        PREALLOC_VUS="$PREALLOC_VUS" MAX_VUS="$MAX_VUS" \
        SUMMARY_OUT="$SPIKE_OUT" \
          taskset -c 4-11 k6 run --quiet spike.js >/dev/null 2>&1 || echo "   (spike k6 non-zero — JSON captured)"
      fi
    else
      echo "   no sustainable RPS found for simple (cache=$cache v=$v) — skipping soak/spike"
    fi
  done
done

echo "==> Aggregating -> CAPACITY.md"
node gen-capacity-report.js "$CAP_DIR" > "$LT_DIR/CAPACITY.md" \
  && echo "   wrote CAPACITY.md" || echo "   report generation failed"

echo "==> Done. Raw: $CAP_DIR  Report: $LT_DIR/CAPACITY.md"
