package org.deltafi.dgs.schedulers;

import org.deltafi.dgs.services.DeltaFilesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
public class ActionResponseScheduler {

    final DeltaFilesService deltaFilesService;

    public ActionResponseScheduler(DeltaFilesService deltaFilesService) {
        this.deltaFilesService = deltaFilesService;
    }

    // convert to milliseconds then divide each interval into 10 samples
    @Scheduled(fixedDelay = 1000)
    public void getIngressResponses() {
        deltaFilesService.getIngressResponses();
    }
}
