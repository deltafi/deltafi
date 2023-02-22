/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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

import Ajv from "ajv";
import addFormats from "ajv-formats";

export default function useIngressRoutingConfiguration() {
  const ajv = new Ajv({ allErrors: true });
  require("ajv-errors")(ajv);
  addFormats(ajv);

  const idSchema = () => {
    return {
      type: ["string", "null"],
    };
  };

  const nameSchema = () => {
    return {
      type: "string",
    };
  };

  const flowSchema = () => {
    return {
      type: ["string", "null"],
    };
  };

  const prioritySchema = () => {
    return { type: "number", minimum: 0 };
  };

  const filenameRegexSchema = () => {
    return {
      type: ["string", "null"],
      format: "regex",
    };
  };

  const requiredMetadataSchema = () => {
    return {
      type: ["array", "null"],
      items: {
        type: "object",
        properties: {
          key: {
            type: "string",
          },
          value: {
            type: "string",
          },
        },
        required: ["key", "value"],
      },
    };
  };

  const requireFileNameOrMetadata = () => {
    return [
      {
        required: ["filenameRegex"],
        properties: {
          filenameRegex: {
            type: "string",
            minLength: 1,
          },
        },
      },
      {
        required: ["requiredMetadata"],
        properties: {
          requiredMetadata: {
            type: "array",
            minItems: 1,
          },
        },
      },
    ];
  };

  const schemaFlowAssignmentRule = () => {
    return {
      type: "object",
      properties: {
        id: { $ref: "#/definitions/idSchema" },
        name: { $ref: "#/definitions/nameSchema" },
        flow: { $ref: "#/definitions/flowSchema" },
        priority: { $ref: "#/definitions/prioritySchema" },
        filenameRegex: { $ref: "#/definitions/filenameRegexSchema" },
        requiredMetadata: { $ref: "#/definitions/requiredMetadataSchema" },
      },
      anyOf: requireFileNameOrMetadata(),
      required: ["name"],
      additionalProperties: false,
      errorMessage: {
        required: "Must have required property 'name'.",
        oneOf: "${0/name} - requires filenameRegex and/or requiredMetadata.",
        additionalProperties: "Must NOT have additional properties.",
        properties: {
          name: "name is a required field.",
          filenameRegex: "${0/name} - not a valid regular expression.",
          requiredMetadata: "${0/name} - not a valid key:value pair.",
        },
      },
    };
  };

  const ingressRoutingFileSchema = {
    type: "array",
    minItems: 1,
    uniqueItems: true,
    items: {
      $ref: "#/definitions/schemaFlowAssignmentRule",
    },
    errorMessage: {
      type: "File uploaded not in required Ingress Routing format.",
      additionalProperties: "Must NOT have additional properties.",
    },
    definitions: {
      schemaFlowAssignmentRule: schemaFlowAssignmentRule(),
      idSchema: idSchema(),
      nameSchema: nameSchema(),
      flowSchema: flowSchema(),
      prioritySchema: prioritySchema(),
      filenameRegexSchema: filenameRegexSchema(),
      requiredMetadataSchema: requiredMetadataSchema(),
    },
  };

  const ingressRoutingSchema = {
    type: "object",
    properties: {
      id: { $ref: "#/definitions/idSchema" },
      name: { $ref: "#/definitions/nameSchema" },
      flow: { $ref: "#/definitions/flowSchema" },
      priority: { $ref: "#/definitions/prioritySchema" },
      filenameRegex: { $ref: "#/definitions/filenameRegexSchema" },
      requiredMetadata: { $ref: "#/definitions/requiredMetadataSchema" },
    },
    definitions: {
      schemaFlowAssignmentRule: schemaFlowAssignmentRule(),
      idSchema: idSchema(),
      nameSchema: nameSchema(),
      flowSchema: flowSchema(),
      prioritySchema: prioritySchema(),
      filenameRegexSchema: filenameRegexSchema(),
      requiredMetadataSchema: requiredMetadataSchema(),
    },
  };

  const ingressRoutingValidator = ajv.compile(ingressRoutingSchema);

  const ingressRoutingFileValidator = ajv.compile(ingressRoutingFileSchema);

  const validateIngressRoute = (uploadedIngressRouting: any) => {
    const ruleToBeValidated = JSON.parse(uploadedIngressRouting);

    const validSingleDeletePolicy = ingressRoutingValidator(ruleToBeValidated);

    if (!validSingleDeletePolicy) {
      return ingressRoutingValidator.errors;
    } else {
      return false;
    }
  };

  const validateIngressRouteFile = (uploadedIngressRoutingFile: any) => {
    const ruleFileToBeValidated = JSON.parse(uploadedIngressRoutingFile);

    const validDeletePolicyFile = ingressRoutingFileValidator(ruleFileToBeValidated);

    if (!validDeletePolicyFile) {
      return ingressRoutingFileValidator.errors;
    } else {
      return false;
    }
  };

  return {
    validateIngressRoute,
    validateIngressRouteFile,
  };
}
