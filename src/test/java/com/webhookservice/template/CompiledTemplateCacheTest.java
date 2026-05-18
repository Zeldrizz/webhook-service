package com.webhookservice.template;

import com.webhookservice.cache.CacheNames;
import com.webhookservice.cache.CaffeineCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CompiledTemplateCacheTest {

    @Test
    void getOrCompile_compilesOnlyOnceForSameTemplate() {
        CompiledTemplateCache cache = cache();
        CountingCompiler compiler = new CountingCompiler();

        CompiledTemplate first = cache.getOrCompile("{{name}}", compiler);
        CompiledTemplate second = cache.getOrCompile("{{name}}", compiler);

        assertSame(first, second);
        assertEquals(1, compiler.count());
    }

    @Test
    void getOrCompile_differentTemplatesUseDifferentEntries() {
        CompiledTemplateCache cache = cache();
        CountingCompiler compiler = new CountingCompiler();

        assertEquals("a", cache.getOrCompile("{{value}}", compiler).render(Map.of("value", "a")));
        assertEquals("b", cache.getOrCompile("{{other}}", compiler).render(Map.of("other", "b")));

        assertEquals(2, compiler.count());
    }

    @Test
    void invalidateAll_forcesRecompile() {
        CompiledTemplateCache cache = cache();
        CountingCompiler compiler = new CountingCompiler();

        cache.getOrCompile("{{name}}", compiler);
        cache.invalidateAll();
        cache.getOrCompile("{{name}}", compiler);

        assertEquals(2, compiler.count());
    }

    private CompiledTemplateCache cache() {
        return new CompiledTemplateCache(new CaffeineCache<>(
                CacheNames.COMPILED_TEMPLATE,
                100,
                Duration.ofSeconds(60),
                true
        ));
    }

    private static class CountingCompiler extends TemplateCompiler {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public CompiledTemplate compile(String template) {
            count.incrementAndGet();
            return super.compile(template);
        }

        int count() {
            return count.get();
        }
    }
}
