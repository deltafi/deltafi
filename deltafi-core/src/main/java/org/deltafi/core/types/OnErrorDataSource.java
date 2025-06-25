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

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ErrorSourceFilter;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.KeyValue;
import org.hibernate.annotations.Type;

import java.util.List;

@Entity
@DiscriminatorValue("ON_ERROR_DATA_SOURCE")
@EqualsAndHashCode(callSuper = true)
@Data
public class OnErrorDataSource extends DataSource {
    private String errorMessageRegex;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<ErrorSourceFilter> sourceFilters;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<KeyValue> metadataFilters;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<KeyValue> annotationFilters;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> includeSourceMetadataRegex;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> includeSourceAnnotationsRegex;

    public OnErrorDataSource() {
        super(FlowType.ON_ERROR_DATA_SOURCE);
    }

    @Override
    public ActionConfiguration findActionConfigByName(String actionName) {
        return null;
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        return List.of();
    }
}