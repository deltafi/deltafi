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
import lombok.*;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "annotations")
@EqualsAndHashCode
public class Annotation implements Comparable<org.deltafi.core.types.Annotation> {
    @Id
    private UUID id = Generators.timeBasedEpochGenerator().generate();
    private String key;
    private String value;

    public Annotation(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public int compareTo(@NotNull Annotation other) {
        int keyComparison = this.key.compareTo(other.key);
        if (keyComparison != 0) {
            return keyComparison;
        }
        return this.value.compareTo(other.value);
    }
}
