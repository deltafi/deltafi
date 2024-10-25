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
package org.deltafi.core.types.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.integration.ExpectedDeltaFile;
import org.deltafi.common.types.integration.IntegrationTest;
import org.deltafi.common.types.integration.TestCaseIngress;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "integration_tests")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationTestEntity {
    @Id
    private String name;
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<PluginCoordinates> plugins;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> dataSources;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> transformationFlows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> dataSinks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<TestCaseIngress> inputs;

    private String timeout;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ExpectedDeltaFile> expectedDeltaFiles;

    public IntegrationTestEntity(IntegrationTest integrationTest) {
        this.name = integrationTest.getName();
        this.description = integrationTest.getDescription();
        this.plugins = integrationTest.getPlugins();
        this.dataSources = integrationTest.getDataSources();
        this.transformationFlows = integrationTest.getTransformationFlows();
        this.dataSinks = integrationTest.getDataSinks();
        this.inputs = integrationTest.getInputs();
        this.timeout = integrationTest.getTimeout();
        this.expectedDeltaFiles = integrationTest.getExpectedDeltaFiles();
    }

    public IntegrationTest toIntegrationTest() {
        IntegrationTest integrationTest = new IntegrationTest();
        integrationTest.setName(this.getName());
        integrationTest.setDescription(this.getDescription());
        integrationTest.setPlugins(this.getPlugins());
        integrationTest.setDataSources(this.getDataSources());
        integrationTest.setTransformationFlows(this.getTransformationFlows());
        integrationTest.setDataSinks(this.getDataSinks());
        integrationTest.setInputs(this.getInputs());
        integrationTest.setTimeout(this.getTimeout());
        integrationTest.setExpectedDeltaFiles(this.getExpectedDeltaFiles());
        return integrationTest;
    }
}
