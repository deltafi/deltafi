/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

const generateData = () => {
  const data =
    "mock-deltafi-stix:\n  egressActions:\n    Stix1_xEgressAction:\n      parameters:\n        dataSink: stix1_x\n        metadataKey: deltafiMetadata\n        url: http://deltafi-egress-sink-service\n      type: org.deltafi.core.action.RestPostEgressAction\n    Stix2_1EgressAction:\n      parameters:\n        dataSink: stix2_1\n        metadataKey: deltafiMetadata\n        url: http://deltafi-egress-sink-service\n      type: org.deltafi.core.action.RestPostEgressAction\n  dataSinks:\n    deltafi-stix.stix2_1:\n      egressAction: Stix2_1EgressAction\n      formatAction: Stix2_1FormatAction\n      validateActions:\n      - Stix2_1ValidateAction\n    deltafi-stix.stix1_x:\n      egressAction: Stix1_xEgressAction\n      formatAction: Stix1_xFormatAction\n      validateActions:\n      - Stix1_xValidateAction\n  flowPlan: deltafi-stix\n  formatActions:\n    Stix1_xFormatAction:\n      type: org.deltafi.stix.actions.Stix1_XFormatAction\n    Stix2_1FormatAction:\n      type: org.deltafi.stix.actions.Stix2_1FormatAction\n      loadAction: Stix2_1LoadAction\n      deltafi-stix.stix1_x:\n      loadAction: Stix2_1LoadAction\n      transformActions:\n      - Stix1_xTo2_1TransformAction\n      loadActions:\n    Stix2_1LoadAction:\n      type: org.deltafi.stix.actions.StixLoadAction\n  transformActions:\n    Stix1_xTo2_1TransformAction:\n      type: org.deltafi.stix.actions.StixTransformAction\n  validateActions:\n    Stix1_xValidateAction:\n      type: org.deltafi.stix.actions.RubberStampValidateAction\n    Stix2_1ValidateAction:\n      type: org.deltafi.stix.actions.RubberStampValidateAction\nsmoke:\n  egressActions:\n    SmokeEgressAction:\n      parameters:\n        dataSink: smoke\n        metadataKey: deltafiMetadata\n        url: http://deltafi-egress-sink-service\n      type: org.deltafi.core.action.RestPostEgressAction\n  dataSinks:\n    smoke.dataSink:\n      egressAction: SmokeEgressAction\n      enrichActions:\n      - SmokeEnrichAction\n      formatAction: SmokeFormatAction\n      validateActions:\n      - SmokeValidateAction\n  enrichActions:\n    SmokeEnrichAction:\n      parameters:\n      type: org.deltafi.passthrough.action.RoteEnrichAction\n  flowPlan: smoke\n  formatActions:\n    SmokeFormatAction:\n      type: org.deltafi.passthrough.action.RoteFormatAction\n      loadAction: SmokeLoadAction\n      transformActions:\n      - SmokeTransformAction\n      loadActions:\n    SmokeLoadAction:\n      parameters:\n      type: org.deltafi.passthrough.action.RoteLoadAction\n  transformActions:\n    SmokeTransformAction:\n      parameters:\n        resultType: binary\n      produces: binary\n      type: org.deltafi.passthrough.action.RoteTransformAction\n  validateActions:\n    SmokeValidateAction:\n      type: org.deltafi.passthrough.action.RubberStampValidateAction\npassthrough:\n  egressActions:\n    PassthroughEgressAction:\n      parameters:\n        dataSink: dataSink\n      type: org.deltafi.core.action.FilterEgressAction\n  dataSinks:\n    passthrough.dataSink:\n      egressAction: PassthroughEgressAction\n      formatAction: PassthroughFormatAction\n      validateActions:\n      - PassthroughValidateAction\n  flowPlan: passthrough\n  formatActions:\n    PassthroughFormatAction:\n      type: org.deltafi.passthrough.action.RoteFormatAction\n      loadAction: PassthroughLoadAction\n      transformActions:\n      - PassthroughTransformAction\n      loadActions:\n    PassthroughLoadAction:\n      parameters:\n      type: org.deltafi.passthrough.action.RoteLoadAction\n  transformActions:\n    PassthroughTransformAction:\n      parameters:\n        resultType: passthrough-binary\n      produces: passthrough-binary\n      type: org.deltafi.passthrough.action.RoteTransformAction\n  validateActions:\n    PassthroughValidateAction:\n      type: org.deltafi.passthrough.action.RubberStampValidateAction\nerror:\n  egressActions:\n    ErrorEgressAction:\n      parameters:\n        dataSink: egress\n        metadataKey: deltafiMetadata\n        url: http://deltafi-egress-sink-service\n      type: org.deltafi.core.action.RestPostEgressAction\n  dataSinks:\n    error.egress:\n      egressAction: ErrorEgressAction\n      formatAction: ErrorFormatAction\n  flowPlan: error\n  formatActions:\n    ErrorFormatAction:\n      type: org.deltafi.core.action.SimpleErrorFormatAction\n";

  return data;
};

export default {
  exportConfigAsYaml: generateData(),
};
