package org.deltafi.core.domain.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.resource.Resource;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.configuration.LoadActionConfiguration;
import org.deltafi.core.domain.configuration.TransformActionConfiguration;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.Variable;
import org.deltafi.core.domain.types.IngressFlow;
import org.deltafi.core.domain.types.IngressFlowPlan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class IngressFlowPlanConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    IngressFlowPlanConverter ingressFlowPlanConverter = new IngressFlowPlanConverter();

    @Test
    void testConverter() throws IOException {
        IngressFlowPlan flowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-ingress-flowplan-test.json"), IngressFlowPlan.class);

        IngressFlow ingressFlow = ingressFlowPlanConverter.toIngressFlow(flowPlan, variables());

        assertThat(ingressFlow.getName()).isEqualTo("passthrough");
        assertThat(ingressFlow.getType()).isEqualTo("binary");
        assertThat(ingressFlow.getDescription()).isEqualTo("Flow that passes data through unchanged");
        assertThat(ingressFlow.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        assertThat(ingressFlow.getFlowStatus().getErrors()).isEmpty();
        assertThat(ingressFlow.getSourcePlugin()).isEqualTo(expectedSourcePlugin());
        assertThat(ingressFlow.getTransformActions()).hasSize(1).contains(expectedTransform());
        assertThat(ingressFlow.getLoadAction()).isEqualTo(expectedLoadAction());
    }

    @Test
    void testUnresolvedPlaceholder() throws IOException {
        IngressFlowPlan ingressFlowPlan = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/convert-ingress-flowplan-test.json"), IngressFlowPlan.class);
        ingressFlowPlan.getLoadAction().setName("${missing.placeholder:defaultignored}");

        IngressFlow ingressFlow = ingressFlowPlanConverter.toIngressFlow(ingressFlowPlan, variables());

        assertThat(ingressFlow.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        FlowConfigError expected = FlowConfigError.newBuilder()
                .configName("${missing.placeholder:defaultignored}")
                .errorType(FlowErrorType.UNRESOLVED_VARIABLE).message("Could not resolve placeholder 'missing.placeholder:defaultignored' in value \"${missing.placeholder:defaultignored}\"").build();

        assertThat(ingressFlow.getFlowStatus().getErrors()).hasSize(1).contains(expected);

    }

    TransformActionConfiguration expectedTransform() {
        TransformActionConfiguration expected = new TransformActionConfiguration();
        expected.setName("passthrough.PassthroughTransformAction");
        expected.setType("org.deltafi.passthrough.action.RoteTransformAction");
        expected.setConsumes("binary");
        expected.setProduces("passthrough-binary");
        expected.setParameters(Map.of("resultType", "passthrough-binary"));
        return expected;
    }

    LoadActionConfiguration expectedLoadAction() {
        LoadActionConfiguration loadActionConfiguration = new LoadActionConfiguration();
        loadActionConfiguration.setName("passthrough.PassthroughLoadAction");
        loadActionConfiguration.setConsumes("passthrough-binary");
        loadActionConfiguration.setType("org.deltafi.passthrough.action.RoteLoadAction");
        loadActionConfiguration.setParameters(Map.of("domains", List.of("binary")));
        return loadActionConfiguration;
    }

    PluginCoordinates expectedSourcePlugin() {
        return PluginCoordinates.builder().artifactId("deltafi-passthrough").version("0.17.0").groupId("org.deltafi.passthrough").build();
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