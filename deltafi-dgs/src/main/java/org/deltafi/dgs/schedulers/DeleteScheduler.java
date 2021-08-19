package org.deltafi.dgs.schedulers;

import lombok.RequiredArgsConstructor;
import org.deltafi.dgs.delete.DeleteRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
public class DeleteScheduler {
    private final DeleteRunner deleteRunner;

    @Scheduled(fixedDelayString = "${deltafi.delete.frequency}")
    public void runDeletes() {
        deleteRunner.runDeletes();
    }
}
