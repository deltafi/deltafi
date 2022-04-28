package org.deltafi.actionkit.service;

import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.config.ActionKitConfig;
import org.deltafi.core.domain.generated.client.RegisterActionsGraphQLQuery;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@ApplicationScoped
public class RegistrationService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Inject
    private ActionKitConfig actionKitConfig;

    @Inject
    private DomainGatewayService domainGatewayService;

    @Inject
    private Instance<Action<?>> actions;

    private GraphQLQuery query;
    private boolean firstTime = true;

    @SuppressWarnings({"unused", "EmptyMethod"})
    public void start(@Observes StartupEvent start) {
        // Needed for quarkus instantiation.
    }

    @PostConstruct
    public void buildRegistration() {
        ActionRegistrationInput input = ActionRegistrationInput.newBuilder()
                .transformActions(new ArrayList<>())
                .loadActions(new ArrayList<>())
                .enrichActions(new ArrayList<>())
                .formatActions(new ArrayList<>())
                .validateActions(new ArrayList<>())
                .egressActions(new ArrayList<>())
                .deleteActions(new ArrayList<>())
                .build();

        actions.forEach(action -> action.registerSchema(input));
        query = RegisterActionsGraphQLQuery.newRequest().actionRegistration(input).build();
        scheduler.scheduleWithFixedDelay(this::registerActions, actionKitConfig.actionRegistrationInitialDelayMs(), actionKitConfig.actionRegistrationPeriodMs(), TimeUnit.MILLISECONDS);
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
