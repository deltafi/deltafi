package org.deltafi.actionkit.endpoint;

import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

@Slf4j
@Path("/echo")
public class EchoEndpoint {
    @POST
    @Consumes({MediaType.MEDIA_TYPE_WILDCARD})
    @Produces(MediaType.TEXT_PLAIN)
    public String echo(String requestBody, @Context HttpHeaders headers) {
        StringBuilder sb = new StringBuilder();
        headers.getRequestHeaders().forEach( (k,v) -> sb.append(k).append(": ").append(v).append("\n"));
        log.info("\n\n---\n" + sb + "---\n" + requestBody + "---\n\n");
        return requestBody;
    }
}