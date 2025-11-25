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
// ABOUTME: Response type for plugin data across all members.
// ABOUTME: Includes both successful plugin data and list of unreachable members.
package org.deltafi.core.types.leader;

import java.util.List;
import java.util.Map;

/**
 * Response containing plugin data from all members.
 *
 * @param plugins Map of member name to their plugins (for successful responses)
 * @param membersNotReporting List of member names that did not return config data
 */
public record PluginsResponse(
        Map<String, List<PluginInfo>> plugins,
        List<String> membersNotReporting
) {}
