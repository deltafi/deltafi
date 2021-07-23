package org.deltafi.dgs.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import org.deltafi.dgs.configuration.ActionConfiguration;
import org.deltafi.dgs.configuration.EgressActionConfiguration;
import org.deltafi.dgs.configuration.EnrichActionConfiguration;
import org.deltafi.dgs.configuration.FormatActionConfiguration;
import org.deltafi.dgs.configuration.LoadActionConfiguration;
import org.deltafi.dgs.configuration.TransformActionConfiguration;
import org.deltafi.dgs.configuration.ValidateActionConfiguration;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.services.ActionConfigService;

import java.util.List;

@DgsComponent
public class ActionConfigDatafetcher {

    private final ActionConfigService actionConfigService;

    public ActionConfigDatafetcher(ActionConfigService actionConfigService) {
        this.actionConfigService = actionConfigService;
    }

    @DgsMutation
    public TransformActionConfiguration registerTransformAction(TransformActionConfigurationInput transformActionConfiguration) {
        return actionConfigService.saveTransformAction(transformActionConfiguration);
    }

    @DgsMutation
    public LoadActionConfiguration registerLoadAction(LoadActionConfigurationInput loadActionConfiguration) {
        return actionConfigService.saveLoadAction(loadActionConfiguration);
    }

    @DgsMutation
    public EnrichActionConfiguration registerEnrichAction(EnrichActionConfigurationInput enrichActionConfiguration) {
        return actionConfigService.saveEnrichAction(enrichActionConfiguration);
    }

    @DgsMutation
    public FormatActionConfiguration registerFormatAction(FormatActionConfigurationInput formatActionConfiguration) {
        return actionConfigService.saveFormatAction(formatActionConfiguration);
    }

    @DgsMutation
    public ValidateActionConfiguration registerValidateAction(ValidateActionConfigurationInput validateActionConfiguration) {
        return actionConfigService.saveValidateAction(validateActionConfiguration);
    }

    @DgsMutation
    public EgressActionConfiguration registerEgressAction(EgressActionConfigurationInput egressActionConfiguration) {
        return actionConfigService.saveEgressAction(egressActionConfiguration);
    }

    @DgsMutation
    public long removeActionConfigs(ActionQueryInput actionQuery) {
        return actionConfigService.removeActionConfigs(actionQuery);
    }

    @DgsQuery
    public List<ActionConfiguration> actionConfigs(ActionQueryInput actionQuery) {
        return actionConfigService.getActionConfigs(actionQuery);
    }
}
