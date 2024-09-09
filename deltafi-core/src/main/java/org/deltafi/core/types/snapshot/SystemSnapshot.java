/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.types.snapshot;

import com.fasterxml.uuid.Generators;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.types.DeletePolicies;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.ResumePolicy;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
public class SystemSnapshot {
    @Id
    private UUID id = Generators.timeBasedEpochGenerator().generate();
    private String reason;
    private OffsetDateTime created = OffsetDateTime.now();
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<PluginVariables> pluginVariables;
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private DeletePolicies deletePolicies;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<KeyValue> deltaFiProperties;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<Link> links;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<RestDataSourceSnapshot> restDataSources = new ArrayList<>();
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<TimedDataSourceSnapshot> timedDataSources = new ArrayList<>();
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<TransformFlowSnapshot> transformFlows = new ArrayList<>();
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<EgressFlowSnapshot> egressFlows = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Set<PluginCoordinates> installedPlugins;
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<PluginImageRepository> pluginImageRepositories = new ArrayList<>();
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<ResumePolicy> resumePolicies = new ArrayList<>();
}
