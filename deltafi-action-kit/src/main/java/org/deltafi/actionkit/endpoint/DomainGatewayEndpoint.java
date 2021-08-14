package org.deltafi.actionkit.endpoint;

import io.quarkus.arc.profile.UnlessBuildProfile;
import org.deltafi.actionkit.service.DomainGatewayService;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/dgs")
@UnlessBuildProfile("prod")
public class DomainGatewayEndpoint {
    @Inject
    DomainGatewayService domainGatewayService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String[] get() {
        return domainGatewayService.getUnsentQueries().toArray(new String[0]);
    }
}
