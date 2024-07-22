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
package org.deltafi.core.plugin.deployer.customization;

import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.plugin.deployer.credential.BasicCredentials;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.services.Snapshotter;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.Result;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
public class PluginCustomizationService implements Snapshotter {

    private static final PluginCustomization EMPTY_CUSTOMIZATIONS = new PluginCustomization();
    private static final String AUTHORIZATION = "Authorization";

    private final PluginCustomizationConfigRepo pluginCustomizationConfigRepo;
    private final PluginCustomizationRepo pluginCustomizationRepo;
    private final CredentialProvider credentialProvider;
    private final HttpClient httpClient;

    public PluginCustomization getPluginCustomizations(PluginCoordinates pluginCoordinates) {
        // Use the cached info if this version of the plugin has been installed before
        PluginCustomization customizations = pluginCustomizationRepo.findByPluginCoordinates(pluginCoordinates)
                .map(PluginCustomizationWithId::getPluginCustomization)
                .orElse(null);

        if (customizations == null) {
            customizations = doFetchPluginCustomizations(pluginCoordinates);
            pluginCustomizationRepo.save(new PluginCustomizationWithId(UUID.randomUUID(), pluginCoordinates, customizations));
        }

        return customizations;
    }

    public List<PluginCustomizationConfig> getAllConfiguration() {
        return pluginCustomizationConfigRepo.findAll();
    }

    public PluginCustomizationConfig save(PluginCustomizationConfig pluginCustomizationConfig) {
        return pluginCustomizationConfigRepo.save(pluginCustomizationConfig);
    }

    public Result delete(String id) {
        if (pluginCustomizationConfigRepo.existsById(id)) {
            pluginCustomizationConfigRepo.deleteById(id);
            return new Result();
        } else {
            return Result.builder().success(false).errors(List.of("No plugin customization config exists with an id of " + id)).build();
        }
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        systemSnapshot.setPluginCustomizationConfigs(getAllConfiguration());
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        if (hardReset) {
            pluginCustomizationConfigRepo.deleteAll();
        }

        pluginCustomizationConfigRepo.saveAll(systemSnapshot.getPluginCustomizationConfigs());
        return new Result();
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.PLUGIN_CUSTOMIZATION_CONFIG_ORDER;
    }

    public static PluginCustomization unmarshalPluginCustomization(String rawCustomization) {
        return Serialization.unmarshal(rawCustomization, PluginCustomization.class);
    }

    private PluginCustomization doFetchPluginCustomizations(PluginCoordinates pluginCoordinates) {
        return pluginCustomizationConfigRepo
                .findById(pluginCoordinates.groupAndArtifact())
                .map(pluginCustomizationConfig -> fetchPluginCustomizations(pluginCoordinates, pluginCustomizationConfig))
                .orElse(EMPTY_CUSTOMIZATIONS);
    }

    @SneakyThrows
    private PluginCustomization fetchPluginCustomizations(PluginCoordinates pluginCoordinates, PluginCustomizationConfig pluginCustomizationConfig) {
        BasicCredentials basicCredentials = pluginCustomizationConfig.getSecretName() != null ?
                credentialProvider.getCredentials(pluginCustomizationConfig.getSecretName()): null;

        URI uri = buildUri(pluginCoordinates, pluginCustomizationConfig.getUrlTemplate());

        return unmarshalPluginCustomization(getAsString(basicCredentials, uri));
    }

    private String getAsString(BasicCredentials credentials, URI location) throws InterruptedException, IOException {

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .GET()
                .uri(location);

        if (credentials != null) {
            requestBuilder.setHeader(AUTHORIZATION, credentials.createBasicAuthString());
        }

        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (200 < response.statusCode() || response.statusCode() > 299) {
                log.error("Failed to retrieve plugin customizations from: {} with response {} - {}", location, response.statusCode(), response.body());
            }
            String responseBody = response.body();
            log.debug("{}", responseBody);
            return responseBody;
        } catch (IOException e) {
            log.error("Failed to retrieve plugin customizations", e);
            throw e;
        } catch (InterruptedException e) {
            log.error("Pulling plugin customizations was interrupted", e);
            throw e;
        }
    }

    private URI buildUri(PluginCoordinates pluginCoordinates, String template) throws URISyntaxException {
        String path = template.replace("${groupId}", pluginCoordinates.getGroupId());
        path = path.replace("${artifactId}", pluginCoordinates.getArtifactId());
        path = path.replace("${version}", pluginCoordinates.getVersion());

        return new URI(path);
    }
}
