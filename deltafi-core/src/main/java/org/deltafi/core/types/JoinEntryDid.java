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
package org.deltafi.core.types;

import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "join_entry_dids")
@Data
public class JoinEntryDid {
    @Id
    private UUID id;

    private UUID joinEntryId;

    @Column(nullable = false)
    private UUID did;

    private String errorReason;
    private boolean orphan = false;
    private String actionName;

    public JoinEntryDid() {
        id = Generators.timeBasedEpochGenerator().generate();
    }

    public JoinEntryDid(UUID joinEntryId, UUID did) {
        id = Generators.timeBasedEpochGenerator().generate();
        this.joinEntryId = joinEntryId;
        this.did = did;
    }
}
