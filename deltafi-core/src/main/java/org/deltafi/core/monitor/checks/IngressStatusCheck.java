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
package org.deltafi.core.monitor.checks;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.exceptions.StorageCheckException;
import org.deltafi.core.generated.types.DataSourceErrorState;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.services.*;
import org.deltafi.core.types.Event;
import org.deltafi.core.types.Event.Severity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@MonitorProfile
@Slf4j
public class IngressStatusCheck extends StatusCheck {

    private final EventService eventService;
    private final SystemService systemService;
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;
    private final DeltaFiPropertiesService propertiesService;

    private boolean ingressDisabledByStorage = false;
    private Set<String> disabledFlows = new HashSet<>();

    public IngressStatusCheck(EventService eventService, SystemService systemService,
                              RestDataSourceService restDataSourceService, TimedDataSourceService timedDataSourceService,
                              DeltaFiPropertiesService deltaFiPropertiesService) {
        super("Ingress Status Check");
        this.eventService = eventService;
        this.systemService = systemService;
        this.restDataSourceService = restDataSourceService;
        this.timedDataSourceService = timedDataSourceService;
        this.propertiesService = deltaFiPropertiesService;
    }

    @Override
    public CheckResult check() {
        ResultBuilder resultBuilder = new ResultBuilder();
        checkForDisabledIngress(resultBuilder);
        checkForStorageDisabledIngress(resultBuilder);
        checkForDisabledFlows(resultBuilder);
        return result(resultBuilder);
    }

    private void checkForDisabledIngress(ResultBuilder resultBuilder) {
        if (propertiesService.getDeltaFiProperties().isIngressEnabled()) {
            return;
        }

        resultBuilder.code(1);
        resultBuilder.addHeader("Ingress is disabled");
        resultBuilder.addLine("Reenable the system property 'ingressEnabled' to restart ingress.");
    }

    private void checkForStorageDisabledIngress(ResultBuilder resultBuilder) {
        try {
            doCheckForStorageDisabledIngress(resultBuilder);
        } catch (StorageCheckException e) {
            resultBuilder.code(1);
            resultBuilder.addHeader("Failed to get storage usage information");
            resultBuilder.addLine(e.getMessage());
        }
    }

    private void doCheckForStorageDisabledIngress(ResultBuilder resultBuilder) throws StorageCheckException {
        long remaining = systemService.contentNodeDiskMetrics().bytesRemaining();
        long required = propertiesService.getDeltaFiProperties().getIngressDiskSpaceRequirementInBytes();

        if (remaining > required) {
            notifyStorageEnabled();
        } else {
            Bytes remainingBytes = Bytes.bytes(remaining);
            Bytes requiredBytes = Bytes.bytes(required);
            notifyStorageDisabled(remainingBytes, requiredBytes);
            resultBuilder.code(1);
            resultBuilder.addHeader("Ingress is disabled due to lack of content storage");
            resultBuilder.addLine("Required bytes in content storage: " + requiredBytes.fullString + " (" + requiredBytes.humanReadable + ")\n");
            resultBuilder.addLine("Remaining bytes in content storage: " + remainingBytes.fullString + " (" + remainingBytes.humanReadable + ")\n");
        }
    }

    private void checkForDisabledFlows(ResultBuilder resultBuilder) {
        List<DataSourceErrorState> errorsExceededFlows = new ArrayList<>(restDataSourceService.dataSourceErrorsExceeded());
        errorsExceededFlows.addAll(timedDataSourceService.dataSourceErrorsExceeded());
        Set<String> errorsExceededFlowNames = errorsExceededFlows.stream()
                .map(DataSourceErrorState::getName).collect(Collectors.toSet());

        for (DataSourceErrorState disabledFlow : errorsExceededFlows) {
            if (!this.disabledFlows.contains(disabledFlow.getName())) {
                notifyFlowDisabled(disabledFlow);
            }
        }

        for (String maybeHealth : this.disabledFlows) {
            if (!errorsExceededFlowNames.contains(maybeHealth)) {
                notifyFlowReenabled(maybeHealth);
            }
        }

        this.disabledFlows = errorsExceededFlowNames;

        if (errorsExceededFlowNames.isEmpty()) {
            return;
        }

        resultBuilder.code(1);
        resultBuilder.addHeader("Ingress is disabled for flows with too many errors");
        resultBuilder.addLine("Acknowledge or resolve errors on these flows to continue:");
        for (DataSourceErrorState disabledFlow : errorsExceededFlows) {
            resultBuilder.addLine("\n- " + disabledFlow.getName() + ": " + disabledFlow.getCurrErrors()
                    + " errors, " + disabledFlow.getMaxErrors() + " allowed");
        }
    }

    private void notifyStorageEnabled() {
        if (!ingressDisabledByStorage) {
            return;
        }

        info("Ingress is re-enabled");
        ingressDisabledByStorage = false;
    }

    private void notifyStorageDisabled(Bytes remaining, Bytes required) {
        if (ingressDisabledByStorage) {
            return;
        }
        String contentLines = "- Remaining bytes in content storage: " + remaining.fullString + " (" + remaining.humanReadable + ")\n" +
                "- Remaining bytes in content storage: " + required.fullString + " (" + required.humanReadable + ")";
        warn("Disabling ingress due to depleted content storage", contentLines);
        ingressDisabledByStorage = true;
    }

    public void notifyFlowDisabled(DataSourceErrorState error) {
        String summary = "Alert: Disabling ingress to flow " + error.getName() + " due to too many errors";
        String content = "- Current errors for flow: " + error.getCurrErrors() +
                "\\n- Maximum errors allowed: " + error.getMaxErrors() + "\\n";

        warn(summary, content);
    }

    public void notifyFlowReenabled(String flow) {
        info("Ingress is re-enabled for flow " + flow);
    }

    private void info(String summary) {
        createEvent(summary, null, Severity.INFO);
    }


    private void warn(String summary, String content) {
        createEvent(summary, content, Severity.WARN);
    }

    private void createEvent(String summary, String content, String severity) {
        eventService.createEvent(Event.builder()
                .summary(summary)
                .content(content)
                .severity(severity)
                .notification(true)
                .source("ingress").build());
    }

    private record Bytes(String fullString, String humanReadable) {
        public static Bytes bytes(long value) {
            return new Bytes(prettyLong(value), humanLong(value));
        }

        private static String prettyLong(long value) {
            return "%,d".formatted(value);
        }

        private static String humanLong(long bytes) {
            if (bytes < 1000) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1000));
            String pre = ("kMGTPE").charAt(exp - 1) + "";
            return String.format("%.1f %sB", bytes / Math.pow(1000, exp), pre);
        }
    }
}
