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

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class DiskMetrics {
    long limit;
    long usage;

    /**
     * Disk % used
     * 50.51% will be returned as 50.51, not 0.5051
     *
     * @return % used
     */
    public double percentUsed() {
        return ((double) usage / limit) * 100;
    }

    /**
     * Disk % used, rounded down
     * 50.51% will be returned as 50, not 0.50
     *
     * @return % used
     */
    public int percentUsedFloor() {
        return (int) Math.floor(this.percentUsed());
    }

    public long bytesOverPercentage(int percent) {
        long targetBytes = (long)((double) percent / 100 * limit);
        return usage - targetBytes;
    }

    /**
     * Number of bytes remaining for content storage
     * @return available space remaining in content storage (in bytes)
     */
    public long bytesRemaining() {
        return limit - usage;
    }
}
