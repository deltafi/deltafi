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
package org.deltafi.core.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PropertyGroup {
    INGRESS_CONTROLS("ingress", "Ingress Controls", ""),
    EGRESS_CONTROLS("egress", "Egress Controls", ""),
    DATA_RETENTION("retention", "Data Retention", ""),
    DATABASE_CONTROLS("database", "Database", ""),
    DATA_FLOW_CONTROLS("dataflow", "Data Flow", ""),
    ERROR_CONTROLS("error", "Error Controls", ""),
    PLUGIN_CONTROLS("plugin", "Plugin Deployments", "Settings that impact when plugins are installed or upgraded"),
    JOIN_CONTROLS("join", "Join Controls", ""),
    METRICS_AND_ANALYTICS("metrics", "Metrics and Analystics", ""),
    PERFORMANCE_CONTROLS("performance", "Performance Controls", ""),
    SYSTEM_MONITORING("system", "System Monitoring", ""),
    UI_CONTROLS("ui", "UI Settings", "Update the banner lines of the UI and time display");

    private final String name;
    private final String label;
    private final String description;
}
