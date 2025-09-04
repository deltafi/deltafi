/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.actionkit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

@Slf4j
public class StartupFailureHandler implements ApplicationListener<ApplicationFailedEvent> {

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        log.error("=== APPLICATION STARTUP FAILED ===");
        log.error("Failure reason: {}", event.getException().getMessage(), event.getException());

        // Log what's keeping JVM alive
        logActiveThreads();

        // Force immediate termination
        log.error("Forcing JVM shutdown due to startup failure");
        System.exit(1);
    }

    private void logActiveThreads() {
        try {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            log.error("Active threads after startup failure:");
            log.error("  Total: {}, Daemon: {}, Non-daemon: {}",
                    threadBean.getThreadCount(),
                    threadBean.getDaemonThreadCount(),
                    threadBean.getThreadCount() - threadBean.getDaemonThreadCount());

            // Log non-daemon thread names for debugging
            ThreadInfo[] threads = threadBean.dumpAllThreads(false, false);
            for (ThreadInfo thread : threads) {
                if (thread.getThreadState() != Thread.State.TERMINATED) {
                    log.error("  - {} ({})", thread.getThreadName(), thread.getThreadState());
                }
            }
        } catch (Exception e) {
            log.error("Could not log thread info", e);
        }
    }
}
