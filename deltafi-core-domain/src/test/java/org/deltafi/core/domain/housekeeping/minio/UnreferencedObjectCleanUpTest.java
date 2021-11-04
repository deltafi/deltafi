package org.deltafi.core.domain.housekeeping.minio;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioObjectStorageService;
import org.deltafi.common.test.storage.s3.minio.DeltafiMinioContainer;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.core.domain.api.repo.DeltaFileRepo;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.deltafi.common.constant.DeltaFiConstants.MINIO_BUCKET;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired)) // SpringBootTest doesn't like @Inject, must use @Autowired
public class UnreferencedObjectCleanUpTest {
    private static final DeltafiMinioContainer DELTAFI_MINIO_CONTAINER = new DeltafiMinioContainer("accessKey", "secretKey");
    private static final MinioClient MINIO_CLIENT = DELTAFI_MINIO_CONTAINER.start(MINIO_BUCKET);
    private static final MinioObjectStorageService MINIO_OBJECT_STORAGE_SERVICE = new MinioObjectStorageService(MINIO_CLIENT, 100_000_000L);

    @TestConfiguration
    public static class Config {
        @Bean
        public ObjectStorageService objectStorageService() {
            return MINIO_OBJECT_STORAGE_SERVICE;
        }

        @Bean
        public Clock clock() {
            return new TestClock(Instant.now(), ZoneId.of("Z"));
        }
    }

    private final DeltaFileRepo deltaFileRepo;
    private final Clock clock;
    private final UnreferencedObjectCleanUp unreferencedObjectCleanUp;

    @AfterAll
    public static void teardownClass() {
        DELTAFI_MINIO_CONTAINER.stop();
    }

    @BeforeEach
    public void setUp() throws ObjectStorageException {
        ((TestClock) clock).setInstant(Instant.now());

        // Can't control the clock for minio, so these have lastModified dates according to the real clock!
        MINIO_OBJECT_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-1/test-action-1a", "test data 1a".getBytes());
        MINIO_OBJECT_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-2/test-action-2a", "test data 2a".getBytes());
        MINIO_OBJECT_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-3/test-action-3a", "test data 3a".getBytes());
        MINIO_OBJECT_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-3/test-action-3b", "test data 3b".getBytes());
    }

    @AfterEach
    public void tearDown() {
        deltaFileRepo.deleteAll();

        MINIO_OBJECT_STORAGE_SERVICE.removeObjects(MINIO_BUCKET, "");
    }

    @Test
    public void unreferencedMinioObjectsOfMinimumAgeAreRemoved() {
        deltaFileRepo.save(DeltaFile.newBuilder().did("did-1").build());
        deltaFileRepo.save(DeltaFile.newBuilder().did("did-2").build());

        runTest(2);
    }

    @Test
    public void allMinioObjectsOfMinimumAgeAreRemovedWhenDeltaFileRepoIsEmpty() {
        runTest(0);
    }

    @Test
    public void noMinioObjectsOfMinimumAgeAreRemovedWhenAllAreReferenced() {
        deltaFileRepo.save(DeltaFile.newBuilder().did("did-1").build());
        deltaFileRepo.save(DeltaFile.newBuilder().did("did-2").build());
        deltaFileRepo.save(DeltaFile.newBuilder().did("did-3").build());

        runTest(4);
    }

    private void runTest(int expectedNumberRemaining) {
        unreferencedObjectCleanUp.removeUnreferencedObjects();

        // Objects have not reached minimum age for removal, so all should still be there.
        assertEquals(4, MINIO_OBJECT_STORAGE_SERVICE.getObjectNames(MINIO_BUCKET, "did-").size());

        ((TestClock) clock).setInstant(clock.instant().plusSeconds(10));
        unreferencedObjectCleanUp.removeUnreferencedObjects();

        // Objects have reached minimum age for removal, so only the expected number should still be there.
        assertEquals(expectedNumberRemaining, MINIO_OBJECT_STORAGE_SERVICE.getObjectNames(MINIO_BUCKET, "did-").size());
    }
}
