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
package org.deltafi.core.types;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record NodeMetrics(String name, Map<String, Map<String, Long>> resources, List<AppName> apps) {

    public NodeMetrics(String name) {
        this(name, new LinkedHashMap<>(), new ArrayList<>());
    }

    public void addMetric(String resource, String metricName, Long value) {
        Map<String, Long> metrics = resources.computeIfAbsent(resource, r -> new LinkedHashMap<>());
        metrics.put(metricName, value);
    }

    public void addApps(List<AppName> apps) {
        this.apps.addAll(apps);
    }
}
