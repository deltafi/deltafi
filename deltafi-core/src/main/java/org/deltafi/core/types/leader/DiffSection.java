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
// ABOUTME: Data record for a section of configuration differences.
// ABOUTME: Groups related diff items by category (plugins, data sinks, etc.).
package org.deltafi.core.types.leader;

import java.util.List;

/**
 * A section of configuration differences grouped by category.
 *
 * @param name      The section name (e.g., "plugins", "dataSinks", "deltaFiProperties")
 * @param diffCount The number of differences in this section
 * @param diffs     The individual difference items
 */
public record DiffSection(
        String name,
        int diffCount,
        List<DiffItem> diffs
) {
    public DiffSection(String name, List<DiffItem> diffs) {
        this(name, diffs.size(), diffs);
    }
}
