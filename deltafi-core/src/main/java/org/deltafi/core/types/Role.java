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

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.uuid.Generators;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.*;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "roles")
@EqualsAndHashCode
public class Role {
    @Id
    private UUID id;
    private String name;

    @Singular
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> permissions;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @ManyToMany(mappedBy = "roles", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH})
    @JsonBackReference
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<DeltaFiUser> users = new HashSet<>();

    public Role(Input roleInput) {
        this.id = Generators.timeBasedEpochGenerator().generate();
        this.name = roleInput.name;
        this.permissions = roleInput.permissions;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
    }

    public Role(String name) {
        this.id = Generators.timeBasedEpochGenerator().generate();
        this.name = name;
        this.permissions = new ArrayList<>();
        this.users = new HashSet<>();
    }

    public record Input(String name, List<String> permissions) {
        public boolean noChanges() {
            return permissions == null && StringUtils.isBlank(name);
        }

        public void update(Role role) {
            Optional.ofNullable(name).ifPresent(role::setName);
            Optional.ofNullable(permissions).ifPresent(role::setPermissions);
            role.setUpdatedAt(OffsetDateTime.now());
        }
    }
}
