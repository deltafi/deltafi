package org.deltafi.actionkit.endpoint;

import io.quarkus.arc.profile.UnlessBuildProfile;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.core.domain.generated.types.ObjectReference;
import org.jboss.resteasy.reactive.RestQuery;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/content/{bucket}/{filename}")
@UnlessBuildProfile("prod")
public class ContentEndpoint {
    @PathParam("bucket")
    private String bucket;

    @PathParam("filename")
    private String filename;

    @Inject
    ObjectStorageService objectStorageService;

    @POST
    @Consumes(MediaType.MEDIA_TYPE_WILDCARD)
    @Produces(MediaType.MEDIA_TYPE_WILDCARD)
    public ObjectReference add(byte[] object) throws ObjectStorageException {
        objectStorageService.putObject(bucket, filename, object);

        return ObjectReference.newBuilder()
                .bucket(bucket)
                .name(filename)
                .size(object.length)
                .build();
    }

    @GET
    @Produces(MediaType.MEDIA_TYPE_WILDCARD)
    public byte[] get(@RestQuery int offset, @RestQuery int size) throws ObjectStorageException {
        return objectStorageService.getObject(bucket, filename, offset, size);
    }
}