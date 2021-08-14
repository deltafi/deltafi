package org.deltafi.actionkit.endpoint;

import io.quarkus.arc.profile.UnlessBuildProfile;
import org.deltafi.actionkit.service.ContentService;
import org.deltafi.dgs.generated.types.ObjectReference;
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
    ContentService contentService;

    @POST
    @Consumes(MediaType.MEDIA_TYPE_WILDCARD)
    @Produces(MediaType.MEDIA_TYPE_WILDCARD)
    public ObjectReference add(byte[] object) {
        ObjectReference objectReference = ObjectReference.newBuilder()
                .bucket(bucket)
                .name(filename)
                .size(object.length)
                .offset(0)
                .build();
        contentService.putObject(objectReference, object);
        return objectReference;
    }

    @GET
    @Produces(MediaType.MEDIA_TYPE_WILDCARD)
    public byte[] get(@RestQuery int offset, @RestQuery int size) {
        ObjectReference objectReference = ObjectReference.newBuilder()
                .bucket(bucket)
                .name(filename)
                .size(size)
                .offset(offset)
                .build();
        return contentService.retrieveContent(objectReference);
    }
}