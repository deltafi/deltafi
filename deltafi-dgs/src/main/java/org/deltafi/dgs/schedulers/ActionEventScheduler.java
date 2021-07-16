package org.deltafi.dgs.schedulers;

import org.deltafi.dgs.services.DeltaFilesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
public class ActionEventScheduler {

    final DeltaFilesService deltaFilesService;

    public ActionEventScheduler(DeltaFilesService deltaFilesService) {
        this.deltaFilesService = deltaFilesService;
    }

    @Scheduled(fixedDelay = 1000)
    public void getActionEvents() {
        deltaFilesService.getActionEvents();
    }
}
