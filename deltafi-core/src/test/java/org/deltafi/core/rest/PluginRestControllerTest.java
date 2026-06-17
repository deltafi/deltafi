/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Tests for the /plugins registration endpoint.
// ABOUTME: Pins the lenient null-to-primitive deserialization that older Go/Python plugins rely on.
package org.deltafi.core.rest;

import org.deltafi.core.configuration.JacksonConfig;
import org.deltafi.core.integration.IntegrationService;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.services.PluginService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PluginRestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JacksonConfig.class)
class PluginRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PluginService pluginService;

    @MockitoBean
    private IntegrationService integrationService;

    @MockitoBean
    private DeployerService deployerService;

    @Test
    void registrationAcceptsJsonNullForPrimitiveBooleanFields() throws Exception {
        // Older Go and Python action kits emit explicit JSON null for some fields that
        // map to Java primitive booleans (Variable.required, Variable.masked,
        // ActionDescriptor.supportsJoin, LookupTable service flags). Spring Boot 4's
        // stricter Jackson defaults reject this without an explicit feature toggle.
        when(pluginService.isRegistrationNew(any())).thenReturn(false);

        String body = """
                {
                  "image": "test:1.0",
                  "pluginCoordinates": {"groupId": "org.test", "artifactId": "test", "version": "1.0"},
                  "displayName": "Test",
                  "description": "Test plugin",
                  "actionKitVersion": "1.0",
                  "dependencies": [],
                  "actions": [
                    {"name": "X", "type": "TRANSFORM", "supportsJoin": null, "schema": {}}
                  ],
                  "variables": [
                    {"name": "v", "dataType": "STRING", "required": null, "masked": null}
                  ],
                  "flowPlans": [],
                  "lookupTables": [
                    {
                      "name": "lt",
                      "columns": ["c"],
                      "keyColumns": ["c"],
                      "serviceBacked": null,
                      "backingServiceActive": null,
                      "pullThrough": null
                    }
                  ],
                  "integrationTests": []
                }
                """;

        mockMvc.perform(post("/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
