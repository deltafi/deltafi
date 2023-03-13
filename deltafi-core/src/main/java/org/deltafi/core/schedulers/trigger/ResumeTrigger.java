/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.schedulers.trigger;

import org.deltafi.core.services.DeltaFiPropertiesService;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Calculates the next execution time based on the autoResumeCheckFrequency in the DeltaFiProperties.
 */
@Service
public class ResumeTrigger extends ConfigurableFixedDelayTrigger {

    public ResumeTrigger(DeltaFiPropertiesService deltaFiPropertiesService) {
        super(deltaFiPropertiesService, (props) -> props.getAutoResumeCheckFrequency(), 15_000L);
    }

}
