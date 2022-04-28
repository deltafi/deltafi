package org.deltafi.core.domain.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.resource.Resource;
import org.deltafi.core.domain.configuration.EgressActionConfiguration;
import org.deltafi.core.domain.configuration.EnrichActionConfiguration;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.configuration.ValidateActionConfiguration;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.Variable;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.EgressFlowPlan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class EgressFlowPlanConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    EgressFlowPlanConverter egressFlowPlanConverter = new EgressFlowPlanConverter();

    @Test
    void testConverter() throws IOException {
        EgressFlowPlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-egress-flowplan-test.json"), EgressFlowPlan.class);
        EgressFlow egressFlow = egressFlowPlanConverter.toEgressFlow(flowPlan, variables());

        assertThat(egressFlow.getName()).isEqualTo("passthrough");
        assertThat(egressFlow.getEgressAction()).isEqualTo(expectedEgressAction());
        assertThat(egressFlow.getFormatAction()).isEqualTo(expectedFormatAction());
        assertThat(egressFlow.getEnrichActions()).hasSize(1).contains(expectedEnrichAction());
        assertThat(egressFlow.getValidateActions()).hasSize(1).contains(expectedValidateAction());
        assertThat(egressFlow.getIncludeIngressFlows()).hasSize(1).contains("ingressFlow");
        assertThat(egressFlow.getExcludeIngressFlows()).hasSize(1).contains("otherFlow");
    }

    @Test
    void testUnresolvedPlaceholder() throws IOException {
        EgressFlowPlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-egress-flowplan-test.json"), EgressFlowPlan.class);
        flowPlan.getFormatAction().setName("${missing.placeholder:defaultignored}");
        EgressFlow egressFlow = egressFlowPlanConverter.toEgressFlow(flowPlan, variables());

        assertThat(egressFlow.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        FlowConfigError expected = FlowConfigError.newBuilder()
                .configName("${missing.placeholder:defaultignored}")
                .errorType(FlowErrorType.UNRESOLVED_VARIABLE).message("Could not resolve placeholder 'missing.placeholder:defaultignored' in value \"${missing.placeholder:defaultignored}\"").build();

        assertThat(egressFlow.getFlowStatus().getErrors()).hasSize(1).contains(expected);
    }

    EnrichActionConfiguration expectedEnrichAction() {
        EnrichActionConfiguration enrichActionConfiguration = new EnrichActionConfiguration();
        enrichActionConfiguration.setName("passthrough.PassthroughEnrichAction");
        enrichActionConfiguration.setType("org.deltafi.passthrough.action.RoteEnrichAction");
        enrichActionConfiguration.setRequiresDomains(List.of("binary"));
        enrichActionConfiguration.setRequiresEnrichment(List.of("binary"));
        enrichActionConfiguration.setParameters(Map.of("enrichments", Map.of("passthroughEnrichment", "customized enrichment value")));
        return enrichActionConfiguration;
    }

    FormatActionConfiguration expectedFormatAction() {
        FormatActionConfiguration formatActionConfiguration = new FormatActionConfiguration();
        formatActionConfiguration.setName("passthrough.PassthroughFormatAction");
        formatActionConfiguration.setType("org.deltafi.passthrough.action.RoteFormatAction");
        formatActionConfiguration.setRequiresDomains(List.of("binary"));
        formatActionConfiguration.setRequiresEnrichment(List.of("binary"));
        return formatActionConfiguration;
    }

    ValidateActionConfiguration expectedValidateAction() {
        ValidateActionConfiguration validateActionConfiguration = new ValidateActionConfiguration();
        validateActionConfiguration.setName("passthrough.PassthroughValidateAction");
        validateActionConfiguration.setType("org.deltafi.passthrough.action.RubberStampValidateAction");
        return validateActionConfiguration;
    }

    EgressActionConfiguration expectedEgressAction() {
        EgressActionConfiguration egressActionConfiguration = new EgressActionConfiguration();
        egressActionConfiguration.setName("passthrough.PassthroughEgressAction");
        egressActionConfiguration.setType("org.deltafi.core.action.RestPostEgressAction");
        egressActionConfiguration.setParameters(Map.of("egressFlow", "egressFlow", "metadataKey", "deltafiMetadata", "url", "http://deltafi-egress-sink-service"));
        return egressActionConfiguration;
    }

    List<Variable> variables() {
        return List.of(
                Variable.newBuilder().name("incoming.type").defaultValue("binary").build(),
                Variable.newBuilder().name("transform.produces").defaultValue("passthrough-binary").build(),
                Variable.newBuilder().name("domain.type").defaultValue("binary").build(),
                Variable.newBuilder().name("enrichment.value").defaultValue("enrichment value").value("customized enrichment value").build(),
                Variable.newBuilder().name("egressUrl").defaultValue("http://deltafi-egress-sink-service").build());
    }
}