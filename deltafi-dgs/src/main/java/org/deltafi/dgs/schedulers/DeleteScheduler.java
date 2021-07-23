package org.deltafi.dgs.schedulers;

import org.deltafi.dgs.delete.DeleteRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// TODO: change this to matchIfMissing = true once the DeleteAction exists
@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = false)
@Service
@EnableScheduling
public class DeleteScheduler {
    private final DeleteRunner deleteRunner;

    public DeleteScheduler(DeleteRunner deleteRunner) {
        this.deleteRunner = deleteRunner;
    }

    @Scheduled(fixedDelayString = "#{deltaFiProperties.getDelete().getFrequency()}")
    public void runDeletes() {
        deleteRunner.runDeletes();
    }
}
