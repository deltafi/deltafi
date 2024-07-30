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
package org.deltafi.core.rest;

import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.plugin.generator.JavaPluginGenerator;
import org.deltafi.core.plugin.generator.PluginGeneratorInput;
import org.deltafi.core.plugin.generator.PluginLanguage;
import org.deltafi.core.security.NeedsPermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.ws.rs.core.MediaType;
import java.io.IOException;

@RestController
public class PluginGeneratorRest {

    private final JavaPluginGenerator javaPluginGenerator;
    private final CoreAuditLogger auditLogger;

    public PluginGeneratorRest(JavaPluginGenerator javaPluginGenerator, CoreAuditLogger auditLogger) {
        this.javaPluginGenerator = javaPluginGenerator;
        this.auditLogger = auditLogger;
    }

    @NeedsPermission.PluginsView
    @PostMapping(value = "generate/plugin", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_OCTET_STREAM)
    public @ResponseBody byte[] generatePlugin(@RequestBody PluginGeneratorInput pluginGeneratorInput) {
        try {
            auditLogger.audit("generating plugin {}:{}", pluginGeneratorInput.getGroupId(), pluginGeneratorInput.getArtifactId());
            pluginGeneratorInput.validate();
            if (PluginLanguage.JAVA.equals(pluginGeneratorInput.getPluginLanguage())) {
                return javaPluginGenerator.generateProject(pluginGeneratorInput).toByteArray();
            } else {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Plugin Language: " + pluginGeneratorInput.getPluginLanguage() + " is not supported");
            }
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, illegalArgumentException.getMessage());
        } catch (IOException ioException) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create the plugin");
        }
    }

}
