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
package org.deltafi.core.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("deltafi")
@Data
public class DeltaFiProperties {
    @Data
    public static class DeleteProperties {
        private Duration frequency = Duration.ofMinutes(10);
        private boolean onCompletion = false;
        private int policyBatchSize = 1000;
    }

    private int requeueSeconds = 30;
    private int coreServiceThreads = 16;
    private Duration deltaFileTtl = Duration.ofDays(14);
    private DeleteProperties delete = new DeleteProperties();
    private String apiUrl;
    private String systemName;
    private PluginConfig plugins;
}
