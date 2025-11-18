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
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.services.DeltaFiPropertiesService;

import static org.deltafi.core.monitor.checks.CheckResult.CODE_YELLOW;

@MonitorProfile
@Slf4j
public class EgressStatusCheck extends StatusCheck {

    private final DeltaFiPropertiesService propertiesService;

    public EgressStatusCheck(DeltaFiPropertiesService deltaFiPropertiesService) {
        super("Egress Status Check");
        this.propertiesService = deltaFiPropertiesService;
    }

    @Override
    public CheckResult check() {
        ResultBuilder resultBuilder = new ResultBuilder();
        checkForDisabledIngress(resultBuilder);
        return result(resultBuilder);
    }

    private void checkForDisabledIngress(ResultBuilder resultBuilder) {
        if (propertiesService.getDeltaFiProperties().isEgressEnabled()) {
            return;
        }

        resultBuilder.code(CODE_YELLOW);
        resultBuilder.addHeader("Egress is disabled");
        resultBuilder.addLine("Reenable the system property 'egressEnabled' to resume egress.");
    }

}
