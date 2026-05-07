package com.webhookservice.cache;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CacheInvalidationCodecTest {

    private final CacheInvalidationCodec codec = new CacheInvalidationCodec();

    @Test
    void encodeDecode_roundTripUpsert() {
        CacheInvalidationEvent original = CacheInvalidationEvent.upsert(UUID.randomUUID(), "slug-1");
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, original);

        CacheInvalidationEvent decoded = codec.decodeFromWire(0, buffer);
        assertEquals(original.kind(), decoded.kind());
        assertEquals(original.id(), decoded.id());
        assertEquals(original.slug(), decoded.slug());
    }

    @Test
    void encodeDecode_roundTripDelete() {
        CacheInvalidationEvent original = CacheInvalidationEvent.delete(UUID.randomUUID(), "delete-slug");
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, original);

        CacheInvalidationEvent decoded = codec.decodeFromWire(0, buffer);
        assertEquals(original, decoded);
    }

    @Test
    void encodeDecode_roundTripFlushAllWithNullFields() {
        CacheInvalidationEvent original = CacheInvalidationEvent.flushAll();
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, original);

        CacheInvalidationEvent decoded = codec.decodeFromWire(0, buffer);
        assertEquals(CacheInvalidationEvent.Kind.FLUSH_ALL, decoded.kind());
        assertNull(decoded.id());
        assertNull(decoded.slug());
    }

    @Test
    void transform_returnsSameInstance() {
        CacheInvalidationEvent original = CacheInvalidationEvent.upsert(UUID.randomUUID(), "x");
        assertSame(original, codec.transform(original));
    }

    @Test
    void systemCodecID_isUserDefined() {
        // -1 indicates the codec is user-defined (Vert.x reserves >= 0 for built-ins).
        assertEquals((byte) -1, codec.systemCodecID());
        assertEquals(CacheInvalidationCodec.NAME, codec.name());
    }
}
