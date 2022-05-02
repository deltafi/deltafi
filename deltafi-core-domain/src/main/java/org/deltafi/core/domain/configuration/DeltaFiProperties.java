/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.core.domain.housekeeping.HousekeepingConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "deltafi")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeltaFiProperties {
    private int requeueSeconds = 30;
    private Duration deltaFileTtl= Duration.ofDays(14);
    private DeleteConfiguration delete = new DeleteConfiguration();
    private Duration actionInactivityThreshold = Duration.ofMinutes(5);
    private HousekeepingConfiguration housekeeping = new HousekeepingConfiguration();
}
