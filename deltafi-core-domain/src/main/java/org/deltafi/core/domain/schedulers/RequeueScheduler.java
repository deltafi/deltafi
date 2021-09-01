package org.deltafi.core.domain.schedulers;

import org.deltafi.core.domain.services.DeltaFilesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
public class RequeueScheduler {

    final DeltaFilesService deltaFilesService;

    public RequeueScheduler(DeltaFilesService deltaFilesService) {
        this.deltaFilesService = deltaFilesService;
    }

    // convert to milliseconds then divide each interval into 10 samples
    @Scheduled(fixedDelayString = "#{deltaFiProperties.getRequeueSeconds() * 1000 / 10}")
    public void requeue() {
        deltaFilesService.requeue();
    }
}