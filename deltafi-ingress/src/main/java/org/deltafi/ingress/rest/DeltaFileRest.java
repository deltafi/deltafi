package org.deltafi.ingress.rest;

import io.minio.messages.NotificationRecords;
import org.deltafi.ingress.service.DeltaFileService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("deltafile")
@Produces(MediaType.APPLICATION_JSON)
public class DeltaFileRest {

    final DeltaFileService deltaFileService;

    public DeltaFileRest(DeltaFileService deltaFileService) {
        this.deltaFileService = deltaFileService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void minioEventListener(NotificationRecords notificationRecords) {
        deltaFileService.processNotificationRecords(notificationRecords);
    }

}
