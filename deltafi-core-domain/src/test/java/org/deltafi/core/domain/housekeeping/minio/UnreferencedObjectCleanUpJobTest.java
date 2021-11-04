package org.deltafi.core.domain.housekeeping.minio;

import lombok.RequiredArgsConstructor;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

import static org.mockito.Mockito.*;

@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired)) // SpringBootTest doesn't like @Inject, must use @Autowired
public class UnreferencedObjectCleanUpJobTest {
    @TestConfiguration
    public static class Config {
        @Bean
        public UnreferencedObjectCleanUp unreferencedObjectCleanUp() {
            return mock(UnreferencedObjectCleanUp.class);
        }
    }

    private final UnreferencedObjectCleanUp unreferencedObjectCleanUp;

    @Test
    public void unreferencedObjectCleanupCalledPeriodically() {
        clearInvocations(unreferencedObjectCleanUp);

        Awaitility.await()
                .atMost(Duration.ofMillis(2999))
                .untilAsserted(() -> verify(unreferencedObjectCleanUp, times(2)).removeUnreferencedObjects());
    }
}
