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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "__typename"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TimedDeletePolicy.class, name = "TimedDeletePolicy")
})
@Entity
@Table(name = "delete_policies")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "policy_type", discriminatorType = DiscriminatorType.STRING)
@Data
@SuperBuilder
@NoArgsConstructor
public abstract class DeletePolicy {
  @Id
  @Builder.Default
  protected UUID id = Generators.timeBasedEpochGenerator().generate();

  @Column(nullable = false, unique = true)
  protected String name;

  @Column(nullable = false)
  protected boolean enabled;

  @Column
  protected String flow;
}
