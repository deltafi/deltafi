package org.deltafi.core.domain.schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.delete.DeleteRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class DeleteScheduler {
    private final DeleteRunner deleteRunner;

    @Scheduled(fixedDelayString = "${deltafi.delete.frequency}")
    public void runDeletes() {
        try {
            deleteRunner.runDeletes();
        } catch (Throwable t) {
            log.error("Unexpected exception while executing scheduled deletes", t);
        }
    }
}