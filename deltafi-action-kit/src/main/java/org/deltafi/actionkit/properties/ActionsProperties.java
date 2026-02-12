/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Configuration properties for action execution (polling, threading, hostname).
// ABOUTME: Bound from the "actions" prefix in application.yaml.
package org.deltafi.actionkit.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for action execution bound from the "actions" prefix
 */
@ConfigurationProperties("actions")
@Data
public class ActionsProperties {
    private long actionPollingInitialDelayMs = 3000L;
    private long actionPollingPeriodMs = 100L;
    // TODO: find a more interactive way to set this via the UI
    // for now, allow customization through the shipped application.yaml
    private Map<String, Integer> actionThreads = new HashMap<>();

    private String hostname;
}
