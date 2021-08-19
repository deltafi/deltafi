package org.deltafi.dgs.schedulers;

import lombok.RequiredArgsConstructor;
import org.deltafi.dgs.services.DeltaFilesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
public class ActionEventScheduler {

    final DeltaFilesService deltaFilesService;

    @Scheduled(fixedDelay = 1000)
    public void processActionEvents() {
        deltaFilesService.processActionEvents();
    }
}
