package com.webhookservice.cache;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** EventBus codec for {@link CacheInvalidationEvent}. Local delivery skips encode via {@link #transform}. */
public final class CacheInvalidationCodec implements MessageCodec<CacheInvalidationEvent, CacheInvalidationEvent> {

    public static final String NAME = "cache-invalidation-event";

    @Override
    public void encodeToWire(Buffer buffer, CacheInvalidationEvent event) {
        byte[] kindBytes = event.kind().name().getBytes(StandardCharsets.UTF_8);
        buffer.appendInt(kindBytes.length).appendBytes(kindBytes);

        if (event.id() != null) {
            buffer.appendByte((byte) 1)
                    .appendLong(event.id().getMostSignificantBits())
                    .appendLong(event.id().getLeastSignificantBits());
        } else {
            buffer.appendByte((byte) 0);
        }

        if (event.slug() != null) {
            byte[] slugBytes = event.slug().getBytes(StandardCharsets.UTF_8);
            buffer.appendByte((byte) 1)
                    .appendInt(slugBytes.length)
                    .appendBytes(slugBytes);
        } else {
            buffer.appendByte((byte) 0);
        }
    }

    @Override
    public CacheInvalidationEvent decodeFromWire(int pos, Buffer buffer) {
        int cursor = pos;
        int kindLen = buffer.getInt(cursor);
        cursor += Integer.BYTES;
        String kindName = new String(buffer.getBytes(cursor, cursor + kindLen), StandardCharsets.UTF_8);
        cursor += kindLen;
        CacheInvalidationEvent.Kind kind = CacheInvalidationEvent.Kind.valueOf(kindName);

        UUID id = null;
        if (buffer.getByte(cursor++) == 1) {
            long msb = buffer.getLong(cursor);
            cursor += Long.BYTES;
            long lsb = buffer.getLong(cursor);
            cursor += Long.BYTES;
            id = new UUID(msb, lsb);
        }

        String slug = null;
        if (buffer.getByte(cursor++) == 1) {
            int slugLen = buffer.getInt(cursor);
            cursor += Integer.BYTES;
            slug = new String(buffer.getBytes(cursor, cursor + slugLen), StandardCharsets.UTF_8);
        }

        return new CacheInvalidationEvent(kind, id, slug);
    }

    @Override
    public CacheInvalidationEvent transform(CacheInvalidationEvent event) {
        return event;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
