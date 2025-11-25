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
// ABOUTME: Data record for a single configuration difference item.
// ABOUTME: Contains the path, type, and values for leader vs member comparison.
package org.deltafi.core.types.leader;

/**
 * A single configuration difference between leader and member.
 *
 * @param path        The path to the differing value (e.g., "plugins[0].version")
 * @param type        The type of difference (ADDED, REMOVED, or MODIFIED)
 * @param leaderValue The value on the leader (null if ADDED on member)
 * @param memberValue The value on the member (null if REMOVED on member)
 */
public record DiffItem(
        String path,
        DiffType type,
        Object leaderValue,
        Object memberValue
) {}
