package org.deltafi.common.storage.s3.minio;

import io.minio.MinioClient;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.test.storage.s3.minio.DeltafiMinioContainer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;

import static org.deltafi.common.constant.DeltaFiConstants.MINIO_BUCKET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MinioObjectStorageServiceTest {
    private static DeltafiMinioContainer DELTAFI_MINIO_CONTAINER;
    private static MinioObjectStorageService MINIO_CLOUD_STORAGE_SERVICE;

    @BeforeAll
    static void setupClass() throws Exception {
        DELTAFI_MINIO_CONTAINER = new DeltafiMinioContainer("accessKey", "secretKey");
        MinioClient minioClient = DELTAFI_MINIO_CONTAINER.start(MINIO_BUCKET);

        MINIO_CLOUD_STORAGE_SERVICE = new MinioObjectStorageService(minioClient, 100_000_000L);
    }

    @AfterAll
    static void teardownClass() {
        DELTAFI_MINIO_CONTAINER.stop();
    }

    @BeforeEach
    void setup() throws ObjectStorageException {
        MINIO_CLOUD_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-1/test-action-1a", "test data 1a".getBytes());
        MINIO_CLOUD_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-1/test-action-1b", "test data 1b".getBytes());
        MINIO_CLOUD_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-2/test-action-2a", "test data 2a".getBytes());
    }

    @AfterEach()
    void teardown() {
        MINIO_CLOUD_STORAGE_SERVICE.removeObjects(MINIO_BUCKET, "");
    }

    @Test
    void testGetObjectNames() {
        List<String> objectNames = MINIO_CLOUD_STORAGE_SERVICE.getObjectNames(MINIO_BUCKET, "did-1");
        assertEquals(2, objectNames.size());
        assertEquals("did-1/test-action-1a", objectNames.get(0));
        assertEquals("did-1/test-action-1b", objectNames.get(1));

        objectNames = MINIO_CLOUD_STORAGE_SERVICE.getObjectNames(MINIO_BUCKET, "did-2");
        assertEquals(1, objectNames.size());
        assertEquals("did-2/test-action-2a", objectNames.get(0));
    }

    @Test
    void testGetObjectNamesWithLastModified() throws ObjectStorageException {
        ZonedDateTime lastModified = ZonedDateTime.now();

        List<String> objectNames = MINIO_CLOUD_STORAGE_SERVICE.getObjectNames(MINIO_BUCKET, "did-1", lastModified);
        assertEquals(2, objectNames.size());
        assertEquals("did-1/test-action-1a", objectNames.get(0));
        assertEquals("did-1/test-action-1b", objectNames.get(1));

        MINIO_CLOUD_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-1/test-action-1c", "test data 1c".getBytes());

        objectNames = MINIO_CLOUD_STORAGE_SERVICE.getObjectNames(MINIO_BUCKET, "did-1", lastModified);
        assertEquals(2, objectNames.size());
        assertEquals("did-1/test-action-1a", objectNames.get(0));
        assertEquals("did-1/test-action-1b", objectNames.get(1));
    }

    @Test
    void testGetObjectAsInputStream() throws ObjectStorageException, IOException {
        InputStream inputStream = MINIO_CLOUD_STORAGE_SERVICE.getObjectAsInputStream(MINIO_BUCKET, "did-1/test-action-1a", 0, 12);
        assertEquals(new String("test data 1a".getBytes()), new String(inputStream.readAllBytes()));
        inputStream.close();

        inputStream = MINIO_CLOUD_STORAGE_SERVICE.getObjectAsInputStream(MINIO_BUCKET, "did-1/test-action-1a", 1, 10);
        assertEquals(new String("est data 1".getBytes()), new String(inputStream.readAllBytes()));
        inputStream.close();
    }

    @Test
    void testGetObject() throws ObjectStorageException {
        byte[] object = MINIO_CLOUD_STORAGE_SERVICE.getObject(MINIO_BUCKET, "did-1/test-action-1a", 0, 12);
        assertEquals(new String("test data 1a".getBytes()), new String(object));

        object = MINIO_CLOUD_STORAGE_SERVICE.getObject(MINIO_BUCKET, "did-1/test-action-1a", 1, 10);
        assertEquals(new String("est data 1".getBytes()), new String(object));
    }

    @Test
    void testRemoveObject() {
        MINIO_CLOUD_STORAGE_SERVICE.removeObject(MINIO_BUCKET, "did-1/test-action-1b");

        List<String> objectNames = MINIO_CLOUD_STORAGE_SERVICE.getObjectNames(MINIO_BUCKET, "did-1");
        assertEquals(1, objectNames.size());
        assertEquals("did-1/test-action-1a", objectNames.get(0));
    }

    @Test
    void testRemoveObjects() {
        assertTrue(MINIO_CLOUD_STORAGE_SERVICE.removeObjects(MINIO_BUCKET, "did-1"));

        List<String> objectNames = MINIO_CLOUD_STORAGE_SERVICE.getObjectNames(MINIO_BUCKET, "did-1");
        assertTrue(objectNames.isEmpty());
    }

    @Test
    void testGetObjectSize() throws ObjectStorageException {
        assertEquals(5, MINIO_CLOUD_STORAGE_SERVICE.getObjectSize(
                MINIO_CLOUD_STORAGE_SERVICE.putObject(MINIO_BUCKET, "name", "12345".getBytes())));
    }
}
