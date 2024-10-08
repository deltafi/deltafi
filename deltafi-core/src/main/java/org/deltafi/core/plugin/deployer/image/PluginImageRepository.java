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
package org.deltafi.core.plugin.deployer.image;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.Set;

@Data
@Entity
@Table(name = "plugin_image_repository", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"plugin_group_ids"})
})
public class PluginImageRepository {
    @Id
    private String imageRepositoryBase;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> pluginGroupIds;
    private String imagePullSecret;

    public void setImageRepositoryBase(String imageRepositoryBase) {
        if (imageRepositoryBase != null && !imageRepositoryBase.isEmpty() && !imageRepositoryBase.endsWith("/")) {
            imageRepositoryBase = imageRepositoryBase + "/";
        }

        this.imageRepositoryBase = imageRepositoryBase;
    }
}
