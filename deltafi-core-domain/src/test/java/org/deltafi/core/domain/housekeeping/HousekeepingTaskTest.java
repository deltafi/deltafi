package org.deltafi.core.domain.housekeeping;

import io.minio.MinioClient;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioObjectStorageService;
import org.deltafi.common.test.storage.s3.minio.DeltafiMinioContainer;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.services.DeltaFilesService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.deltafi.common.constant.DeltaFiConstants.MINIO_BUCKET;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = { "deltafi.housekeeping.initialDelaySeconds=0", "deltafi.housekeeping.delaySeconds=1",
        "deltafi.housekeeping.objectMinimumAgeForRemovalSeconds=2" })
public class HousekeepingTaskTest {
    private static DeltafiMinioContainer DELTAFI_MINIO_CONTAINER;
    private static MinioClient MINIO_CLIENT;

    static {
        DELTAFI_MINIO_CONTAINER = new DeltafiMinioContainer("accessKey", "secretKey");
        try {
            MINIO_CLIENT = DELTAFI_MINIO_CONTAINER.start(MINIO_BUCKET);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @TestConfiguration
    public static class Config {
        @Bean
        public ObjectStorageService objectStorageService() {
            return new MinioObjectStorageService(MINIO_CLIENT, 100_000_000L);
        }

        @Bean
        public Clock clock() {
            return new TestClock(Instant.now(), ZoneId.of("Z"));
        }
    }

    @Inject
    private ObjectStorageService objectStorageService;

    @Inject
    private DeltaFilesService deltaFilesService;

    @Inject
    private Clock clock;

    @AfterAll
    static void teardownClass() {
        DELTAFI_MINIO_CONTAINER.stop();
    }

    @Test
    public void unreferencedMinioObjectsAreRemovedPeriodically() throws ObjectStorageException, InterruptedException {
        ((TestClock) clock).setInstant(Instant.now());

        objectStorageService.putObject(MINIO_BUCKET, "did-1/test-action-1a", "test data 1a".getBytes());
        objectStorageService.putObject(MINIO_BUCKET, "did-2/test-action-2a", "test data 2a".getBytes());
        objectStorageService.putObject(MINIO_BUCKET, "did-3/test-action-3a", "test data 3a".getBytes());
        objectStorageService.putObject(MINIO_BUCKET, "did-3/test-action-3b", "test data 3b".getBytes());

        deltaFilesService.addDeltaFile(DeltaFile.newBuilder().did("did-1").build());
        deltaFilesService.addDeltaFile(DeltaFile.newBuilder().did("did-2").build());

        Thread.sleep(1500); // allow time for the scheduled task to execute

        // objects have not reached minimum age for removal
        assertEquals(2, objectStorageService.getObjectNames(MINIO_BUCKET, "did-3").size());

        ((TestClock) clock).setInstant(clock.instant().plusSeconds(3));

        Thread.sleep(1500); // allow time for the scheduled task to execute

        assertEquals(0, objectStorageService.getObjectNames(MINIO_BUCKET, "did-3").size());
    }
}
