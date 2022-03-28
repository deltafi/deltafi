package org.deltafi.ingress.rest;

import lombok.RequiredArgsConstructor;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.ingress.exceptions.DeltafiException;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;
import org.deltafi.ingress.service.DeltaFileService;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Objects;

@Path("deltafile")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class DeltaFileRest {
    private final DeltaFileService deltaFileService;

    @POST
    @Path("ingress")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    public Response ingressData(InputStream dataStream, @Context HttpHeaders headers,
                                @QueryParam("filename") String filenameFromQueryParam, @QueryParam("flow") String flowFromQueryParam,
                                @HeaderParam("Filename") String filenameFromHeader, @HeaderParam("Flow") String flowFromHeader,
                                @HeaderParam("Metadata") String metadata) {
        try {
            String flow = getParam(flowFromQueryParam, flowFromHeader, "Flow");
            String filename = getParam(filenameFromQueryParam, filenameFromHeader, "Filename");
            String did = deltaFileService.ingressData(dataStream, filename, flow, metadata, headers.getMediaType().getType() + "/" + headers.getMediaType().getSubtype());
            return Response.ok(did).build();
        } catch (ObjectStorageException | DeltafiGraphQLException | DeltafiException exception) {
            return Response.status(500).entity(exception.getMessage()).build();
        } catch (DeltafiMetadataException exception) {
            return Response.status(400).entity(exception.getMessage()).build();
        }
    }

    private String getParam(String queryParam, String headerParam, String paramName) throws DeltafiMetadataException {
        if (Objects.isNull(queryParam) && Objects.isNull(headerParam)) {
            throw new DeltafiMetadataException(paramName + " must be passed in as a query parameter or header");
        }

        return Objects.nonNull(queryParam) ? queryParam : headerParam;
    }
}