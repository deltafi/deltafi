/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.configuration.server.environment.factory;

import org.deltafi.core.configuration.server.environment.GitEnvironmentRepository;
import org.deltafi.core.configuration.server.service.StateHolderService;
import org.eclipse.jgit.transport.HttpTransport;
import org.springframework.cloud.config.server.environment.ConfigurableHttpConnectionFactory;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.cloud.config.server.support.TransportConfigCallbackFactory;
import org.springframework.core.env.ConfigurableEnvironment;

public class GitEnvironmentRepositoryFactory
        implements EnvironmentRepositoryFactory<GitEnvironmentRepository, MultipleJGitEnvironmentProperties> {

    private final ConfigurableEnvironment environment;
    private final ConfigurableHttpConnectionFactory connectionFactory;
    private final TransportConfigCallbackFactory transportConfigCallbackFactory;
    private final StateHolderService stateHolderService;
    private final String label;


    public GitEnvironmentRepositoryFactory(ConfigurableEnvironment environment,
                                           ConfigurableHttpConnectionFactory connectionFactory,
                                           TransportConfigCallbackFactory transportConfigCallbackFactory,
                                           StateHolderService stateHolderService, String label) {
        this.environment = environment;
        this.connectionFactory = connectionFactory;
        this.transportConfigCallbackFactory = transportConfigCallbackFactory;
        this.stateHolderService = stateHolderService;
        this.label = label;
    }

    @Override
    public GitEnvironmentRepository build(MultipleJGitEnvironmentProperties environmentProperties)
            throws Exception {
        HttpTransport.setConnectionFactory(this.connectionFactory);
        this.connectionFactory.addConfiguration(environmentProperties);

        GitEnvironmentRepository repository = new GitEnvironmentRepository(this.environment,
                environmentProperties, stateHolderService);
        repository.setTransportConfigCallback(transportConfigCallbackFactory.build(environmentProperties));
        repository.setDefaultLabel(this.label);
        return repository;
    }
}
