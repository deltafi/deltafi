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
package org.deltafi.actionkit.service;

import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.config.ActionsProperties;
import org.deltafi.common.types.ActionRegistrationInput;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service that gathers up all the extant actions and registers them with the domain
 */
@Service
@Slf4j
public class RegistrationService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Autowired
    ActionsProperties actionsProperties;

    @Autowired
    DomainGatewayService domainGatewayService;

    @Autowired
    List<Action<?>> actions;

    private GraphQLQuery query;
    private boolean firstTime = true;

    @PostConstruct
    public void buildRegistration() {
        ActionRegistrationInput input = ActionRegistrationInput.newBuilder()
                .transformActions(new ArrayList<>())
                .loadActions(new ArrayList<>())
                .domainActions(new ArrayList<>())
                .enrichActions(new ArrayList<>())
                .formatActions(new ArrayList<>())
                .validateActions(new ArrayList<>())
                .egressActions(new ArrayList<>())
                .build();

        actions.forEach(action -> action.registerSchema(input));
        query = new GraphQLQuery("mutation") {
            @NotNull
            @Override
            public String getOperationName() {
                return "registerActions";
            }
        };
        query.getInput().put("actionRegistration", input);

        scheduler.scheduleWithFixedDelay(this::registerActions, 0,
                actionsProperties.getActionRegistrationPeriodMs(), TimeUnit.MILLISECONDS);
    }

    private void registerActions() {
        try {
            GraphQLResponse response = domainGatewayService.submit(new GraphQLQueryRequest(query, null));
            int result = response.extractValueAsObject("data." + query.getOperationName(), Integer.class);
            if (result <= 0) {
                log.error("No actions registered");
            } else if (firstTime) {
                firstTime = false;
                log.info("Action registration count: " + result);
            }
        } catch (Throwable exception) {
            log.error("Could not send action parameter schema", exception);
        }
    }

}
