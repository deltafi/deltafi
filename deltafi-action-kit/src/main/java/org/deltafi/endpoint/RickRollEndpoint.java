package org.deltafi.endpoint;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.service.RickRollService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Slf4j
@Path("/rickroll")
public class RickRollEndpoint {
    @Inject
    RickRollService rickRollService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String rickroll() {
        return rickRollService.rickroll();
    }
}