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

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.*;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
@EqualsAndHashCode
public class DeltaFiUser {
    @Id
    private UUID id;
    private String name;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String username;
    private String password;
    private String dn;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH}, fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"})
    )
    @Builder.Default
    @JsonManagedReference
    private Set<Role> roles = new HashSet<>();

    public DeltaFiUser(Input input) {
        this.id = Generators.timeBasedEpochGenerator().generate();
        this.name = input.name;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
        this.username = input.username;
        this.password = input.password;
        this.dn = input.dn;
    }

    public DeltaFiUser(String username) {
        this.id = Generators.timeBasedEpochGenerator().generate();
        this.username = username;
        this.roles = new HashSet<>();
    }

    @Builder
    public record Input(String name, String username, String dn, String password, Set<UUID> roleIds) {

        public boolean noChanges() {
            return roleIds == null && StringUtils.isAllBlank(name, username, password, dn);
        }

        public void update(DeltaFiUser user, PasswordEncoder passwordEncoder) {
            Optional.ofNullable(name).ifPresent(user::setName);
            Optional.ofNullable(username).ifPresent(user::setUsername);
            Optional.ofNullable(dn).ifPresent(user::setDn);
            Optional.ofNullable(password).map(passwordEncoder::encode).ifPresent(user::setPassword);
            user.setUpdatedAt(OffsetDateTime.now());
        }
    }
}
