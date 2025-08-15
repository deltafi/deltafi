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
import org.deltafi.common.util.MarkdownBuilder;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.*;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.SslConfigService;
import org.deltafi.core.types.SslInfo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.deltafi.core.monitor.checks.CheckResult.*;

@Slf4j
@Service
@Profile(MonitorProfile.MONITOR)
public class SslCheck extends StatusCheck {

    private static final String MESSAGE_TEMPLATE = "%s Secret **%s** with the cert for **%s** %s. It is used by %s.";

    private final SslConfigService sslConfigService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;

    public SslCheck(SslConfigService sslConfigService, DeltaFiPropertiesService deltaFiPropertiesService) {
        super("SSL Secrets Check");
        this.sslConfigService = sslConfigService;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
    }

    @Override
    public CheckResult check() {
        MarkdownBuilder markdownBuilder = new MarkdownBuilder();

        Collection<SslInfo> sslInfos = sslConfigService.getSslSettings().keys();
        markdownBuilder.addList("**SSL Secrets**", createCertList(sslInfos));

        ResultBuilder resultBuilder = new ResultBuilder();
        resultBuilder.addLine(markdownBuilder.build());
        resultBuilder.code(code(sslInfos));
        return result(resultBuilder);
    }

    private List<String> createCertList(Collection<SslInfo> sslInfos) {
        return sslInfos.stream()
                .sorted(Comparator.comparingLong(SslInfo::daysToExpired))
                .map(this::certMessage).toList();
    }

    private int code(Collection<SslInfo> sslInfos) {
        long minDaysToExpire = sslInfos.stream().mapToLong(SslInfo::daysToExpired).min().orElse(Integer.MAX_VALUE);
        int errorThreshold = deltaFiPropertiesService.getDeltaFiProperties().getCheckSslExpirationErrorThreshold();
        int warningThreshold = deltaFiPropertiesService.getDeltaFiProperties().getCheckSslExpirationWarningThreshold();
        if (minDaysToExpire < errorThreshold) {
            return CODE_RED;
        } else if (minDaysToExpire < warningThreshold) {
            return CODE_YELLOW;
        } else {
            return CODE_GREEN;
        }
    }

    private String certMessage(SslInfo sslInfo) {
        String usedBy = usedBy(sslInfo.usedBy());
        int errorThreshold = deltaFiPropertiesService.getDeltaFiProperties().getCheckSslExpirationErrorThreshold();
        int warningThreshold = deltaFiPropertiesService.getDeltaFiProperties().getCheckSslExpirationWarningThreshold();
        if (sslInfo.cert() != null) {
            String subjectCN = sslInfo.cert().commonName();
            String daysUntilExpired = sslInfo.cert().daysUntilExpiration() + " days";

            String message;
            String prefix;
            if (sslInfo.cert().daysUntilExpiration() < errorThreshold) {
                prefix = "❌";
                message = sslInfo.cert().isExpired() ? " is expired" : " expires in " + daysUntilExpired;
            } else if (sslInfo.cert().daysUntilExpiration() < warningThreshold) {
                prefix = "⚠️";
                message = " expires in " + daysUntilExpired;
            } else {
                prefix = "✅";
                message = " is good for " +  daysUntilExpired;
            }
            return MESSAGE_TEMPLATE.formatted(prefix, sslInfo.secretName(), subjectCN, message, usedBy);
        } else {
            return "ℹ️ Secret **" + sslInfo.secretName() + "** would be used by " + usedBy + " but is not populated";
        }
    }

    private String usedBy(Set<String> usedBy) {
        List<String> usedByList = usedBy != null ? new ArrayList<>(usedBy) : Collections.emptyList();

        return switch (usedByList.size()) {
            case 0 -> "nothing";
            case 1 -> usedByList.getFirst();
            case 2 -> usedByList.getFirst() + " and " + usedByList.getLast();
            default -> String.join(", ", usedByList.subList(0, usedByList.size() - 1)) + " and " + usedByList.getLast();
        };
    }
}
