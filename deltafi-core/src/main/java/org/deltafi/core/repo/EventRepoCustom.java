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
package org.deltafi.core.repo;

import org.deltafi.core.types.Event;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface EventRepoCustom {
    /**
     * Find and update an event with the given id, setting the acknowledged field
     * @param id of the event to update
     * @param acknowledged new value for the acknowledged field
     * @return the updated event if it existed
     */
    Optional<Event> updateAcknowledged(UUID id, boolean acknowledged);

    /**
     * Find the events matching the filters
     * @param filters map that will be converted to search criteria
     * @return list of matching events
     */
    List<Event> findEvents(Map<String, String> filters);
}
