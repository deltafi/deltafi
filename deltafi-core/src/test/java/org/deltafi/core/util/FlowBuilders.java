/**
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
package org.deltafi.core.util;

import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.core.types.IngressFlow;

import java.util.List;

import static org.deltafi.core.util.Constants.EGRESS_FLOW_NAME;
import static org.deltafi.core.util.Constants.INGRESS_FLOW_NAME;

public class FlowBuilders {
    public static IngressFlow buildIngressFlow(FlowState flowState) {
        LoadActionConfiguration lc = new LoadActionConfiguration("sampleIngress.SampleLoadAction", "type");
        TransformActionConfiguration tc = new TransformActionConfiguration("sampleIngress.Utf8TransformAction", "type");
        TransformActionConfiguration tc2 = new TransformActionConfiguration("sampleIngress.SampleTransformAction", "type");

        return buildFlow(INGRESS_FLOW_NAME, lc, List.of(tc, tc2), flowState, false);
    }

    public static IngressFlow buildRunningFlow(String name, LoadActionConfiguration loadActionConfiguration, List<TransformActionConfiguration> transforms, boolean testMode) {
        return buildFlow(name, loadActionConfiguration, transforms, FlowState.RUNNING, testMode);
    }

    public static IngressFlow buildFlow(String name, LoadActionConfiguration loadActionConfiguration, List<TransformActionConfiguration> transforms, FlowState flowState, boolean testMode) {
        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName(name);
        ingressFlow.getFlowStatus().setState(flowState);
        ingressFlow.setLoadAction(loadActionConfiguration);
        ingressFlow.setTransformActions(transforms);
        ingressFlow.setTestMode(testMode);
        return ingressFlow;
    }

    public static EgressFlow buildEgressFlow(FlowState flowState) {
        FormatActionConfiguration sampleFormat = new FormatActionConfiguration("sampleEgress.SampleFormatAction", "type", List.of("sampleDomain"));
        sampleFormat.setRequiresEnrichments(List.of("sampleEnrichment"));

        EgressActionConfiguration sampleEgress = new EgressActionConfiguration("sampleEgress.SampleEgressAction", "type");

        return buildFlow(EGRESS_FLOW_NAME, sampleFormat, sampleEgress, flowState, false);
    }

    public static EnrichFlow buildEnrichFlow(FlowState flowState) {
        EnrichFlow enrichFlow = new EnrichFlow();
        enrichFlow.setName("sampleEnrich");

        DomainActionConfiguration sampleDomain = new DomainActionConfiguration("sampleEnrich.SampleDomainAction", "type", List.of("sampleDomain"));

        EnrichActionConfiguration sampleEnrich = new EnrichActionConfiguration("sampleEnrich.SampleEnrichAction", "type", List.of("sampleDomain"));
        sampleEnrich.setRequiresMetadataKeyValues(List.of(new KeyValue("loadSampleType", "load-sample-type")));

        enrichFlow.setDomainActions(List.of(sampleDomain));
        enrichFlow.setEnrichActions(List.of(sampleEnrich));
        enrichFlow.getFlowStatus().setState(flowState);
        return enrichFlow;
    }

    public static EgressFlow buildRunningFlow(String name, FormatActionConfiguration formatAction, EgressActionConfiguration egressAction, boolean testMode) {
        return buildFlow(name, formatAction, egressAction, FlowState.RUNNING, testMode);
    }

    public static EgressFlow buildFlow(String name, FormatActionConfiguration formatAction, EgressActionConfiguration egressAction, FlowState flowState, boolean testMode) {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName(name);
        egressFlow.setFormatAction(formatAction);
        egressFlow.setEgressAction(egressAction);
        egressFlow.setIncludeIngressFlows(null);
        egressFlow.getFlowStatus().setState(flowState);
        egressFlow.setTestMode(testMode);
        return egressFlow;
    }
}
