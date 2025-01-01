/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.common.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import lombok.Data;
import org.deltafi.common.content.ContentStorageServiceAutoConfiguration;
import org.deltafi.common.http.HttpServiceAutoConfiguration;
import org.deltafi.common.storage.s3.minio.MinioAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = { CacheAutoConfigurationTest.TestApp.class, TestService.class,
        CacheAutoConfigurationTest.TickerConfiguration.class },
        properties = { "cache.caches.test-cache-a=expireAfterWrite=PT3S",
                "cache.caches.test-cache-b=expireAfterWrite=PT5S" },
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableAutoConfiguration(exclude = { ContentStorageServiceAutoConfiguration.class, HttpServiceAutoConfiguration.class,
        MinioAutoConfiguration.class, DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
public class CacheAutoConfigurationTest {
    @SpringBootApplication
    public static class TestApp {
        public static void main(String... args) {
            SpringApplication.run(TestApp.class, args);
        }
    }

    @Data
    public static class TestTicker implements Ticker {
        private long nanoseconds;

        @Override
        public long read() {
            return nanoseconds;
        }
    }

    @TestConfiguration
    public static class TickerConfiguration {
        @Bean
        public Ticker ticker() {
            return new TestTicker();
        }
    }

    @Autowired
    CacheManager cacheManager;

    @Autowired
    TestService testService;

    @Autowired
    TestTicker testTicker;

    @Test
    public void caches() {
        Cache cacheA = cacheManager.getCache("test-cache-a");
        Cache cacheB = cacheManager.getCache("test-cache-b");

        assertNotNull(cacheA);
        assertNotNull(cacheB);

        assertEquals("test-a", testService.lookupA("test"));
        assertEquals("other-a", testService.lookupA("other"));
        assertEquals("test-b", testService.lookupB("test"));
        assertEquals("other-b", testService.lookupB("other"));
        assertEquals(2, testService.getLookupACount());
        assertEquals(2, testService.getLookupBCount());

        assertEquals("test-a", testService.lookupA("test"));
        assertEquals("other-a", testService.lookupA("other"));
        assertEquals("test-b", testService.lookupB("test"));
        assertEquals("other-b", testService.lookupB("other"));
        assertEquals(2, testService.getLookupACount());
        assertEquals(2, testService.getLookupBCount());

        testTicker.setNanoseconds(3_000_000_000L);
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) cacheA.getNativeCache()).cleanUp();
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) cacheB.getNativeCache()).cleanUp();

        assertEquals("test-a", testService.lookupA("test"));
        assertEquals("test-b", testService.lookupB("test"));
        assertEquals(3, testService.getLookupACount());
        assertEquals(2, testService.getLookupBCount());

        testTicker.setNanoseconds(5_000_000_000L);
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) cacheA.getNativeCache()).cleanUp();
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) cacheB.getNativeCache()).cleanUp();

        assertEquals("test-a", testService.lookupA("test"));
        assertEquals("test-b", testService.lookupB("test"));
        assertEquals(3, testService.getLookupACount());
        assertEquals(3, testService.getLookupBCount());
    }
}
