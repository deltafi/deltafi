/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services;


import org.deltafi.core.types.Result;
import org.deltafi.core.types.snapshot.Snapshot;
import org.springframework.core.Ordered;

/**
 * Interface that defines the methods to update a system snapshot
 * and to restore from a system snapshot
 */
public interface Snapshotter extends Ordered {

    /**
     * Update the SystemSnapshot with current system state
     * @param snapshot snapshot that is used to capture the current system state
     */
    void updateSnapshot(Snapshot snapshot);

    /**
     * Reset the system to the state in the Snapshot
     * @param snapshot snapshot that holds the system state at the time of the snapshot
     * @param hardReset when true reset all other custom settings before applying the system snapshot values
     * @return the Result of the reset that will hold any errors or information about the reset
     */
    Result resetFromSnapshot(Snapshot snapshot, boolean hardReset);
}
