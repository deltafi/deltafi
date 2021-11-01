package org.deltafi.core.action.delete;

import io.minio.MinioClient;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioObjectStorageService;
import org.deltafi.common.test.storage.s3.minio.DeltafiMinioContainer;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.deltafi.common.constant.DeltaFiConstants.MINIO_BUCKET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeleteActionTest {
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

    @Test
    void testExecute() throws ObjectStorageException {
        MINIO_CLOUD_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-1/test-action-1a", "test data 1a".getBytes());
        MINIO_CLOUD_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-1/test-action-1b", "test data 1b".getBytes());
        MINIO_CLOUD_STORAGE_SERVICE.putObject(MINIO_BUCKET, "did-2/test-action-2a", "test data 2a".getBytes());

        DeleteAction deleteAction = new DeleteAction(MINIO_CLOUD_STORAGE_SERVICE);
        DeltaFile deltaFile = DeltaFile.newBuilder().did("did-1").build();
        ActionParameters actionParameters = new ActionParameters("name", Collections.emptyMap());
        Result<ActionParameters> result = deleteAction.execute(deltaFile, actionParameters);

        assertTrue(result instanceof DeleteResult);
        assertEquals(deltaFile.getDid(), result.toEvent().getDid());
        assertEquals(actionParameters.getName(), result.toEvent().getAction());

        assertEquals(0, MINIO_CLOUD_STORAGE_SERVICE.getObjectNames(MINIO_BUCKET, "did-1").size());
        assertEquals(1, MINIO_CLOUD_STORAGE_SERVICE.getObjectNames(MINIO_BUCKET, "did-2").size());
    }

    @Test
    void testExecuteError() {
        ObjectStorageService mockObjectStorageService = Mockito.mock(ObjectStorageService.class);
        DeleteAction deleteAction = new DeleteAction(mockObjectStorageService);
        Mockito.when(mockObjectStorageService.removeObjects(Mockito.any(), Mockito.any())).thenReturn(false);

        DeltaFile deltaFile = DeltaFile.newBuilder().did("did-1").build();
        ActionParameters actionParameters = new ActionParameters("name", Collections.emptyMap());
        Result<ActionParameters> result = deleteAction.execute(deltaFile, actionParameters);

        assertTrue(result instanceof ErrorResult);
        assertEquals(deltaFile.getDid(), result.toEvent().getDid());
        assertEquals(actionParameters.getName(), result.toEvent().getAction());
        assertEquals("Unable to remove all objects for delta file.", result.toEvent().getError().getCause());
    }
}
