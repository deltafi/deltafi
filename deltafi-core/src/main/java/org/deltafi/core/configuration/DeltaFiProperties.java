/*
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
package org.deltafi.core.configuration;

import lombok.Data;
import org.deltafi.core.configuration.ui.UiProperties;
import org.deltafi.core.collect.CollectProperties;
import org.springframework.data.annotation.Id;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Data
public class DeltaFiProperties {

    public static final String PROPERTY_ID = "deltafi-properties";

    @Id
    private String id = PROPERTY_ID;
    private String systemName = "DeltaFi";
    private Duration requeueDuration = Duration.ofMinutes(5);
    private Duration autoResumeCheckFrequency = Duration.ofMinutes(1);
    private int coreServiceThreads = 16;
    private int coreInternalQueueSize = 64;
    private int scheduledServiceThreads = 8;
    private MetricsProperties metrics = new MetricsProperties();
    private DeleteProperties delete = new DeleteProperties();
    private DeltaFileCacheProperties deltaFileCache = new DeltaFileCacheProperties();
    private IngressProperties ingress = new IngressProperties();
    private PluginProperties plugins = new PluginProperties();
    private SystemCheckProperties checks = new SystemCheckProperties();
    private UiProperties ui = new UiProperties();
    private int inMemoryQueueSize = 5000;

    private Set<String> setProperties = new HashSet<>();

    private CollectProperties collect = new CollectProperties();
}
