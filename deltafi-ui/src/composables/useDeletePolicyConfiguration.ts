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

import Ajv from "ajv";

export default function useDeletePolicyConfiguration() {
  const ajv = new Ajv({ allErrors: true });
  require("ajv-errors")(ajv);

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

  const enabledSchema = () => {
    return {
      type: "boolean",
    };
  };

  const maxPercentSchema = () => {
    return { type: "number", minimum: 0, maximum: 100 };
  };

  const ISO8601Duration = () => {
    return {
      type: ["string", "null"],
      pattern: "^P([0-9]+(?:[,.][0-9]+)?Y)?([0-9]+(?:[,.][0-9]+)?M)?([0-9]+(?:[,.][0-9]+)?D)?(?:T([0-9]+(?:[,.][0-9]+)?H)?([0-9]+(?:[,.][0-9]+)?M)?([0-9]+(?:[,.][0-9]+)?S)?)?$",
      //pattern: "^P([0-9]+(?:[,.][0-9]+)?Y)?([0-9]+(?:[,.][0-9]+)?M)?([0-9]+(?:[,.][0-9]+)?D)?(?:T([0-9]+(?:[,.][0-9]+)?H)?([0-9]+(?:[,.][0-9]+)?M)?([0-9]S)?)?$",
      //pattern: "^P(?=\d|T\d)(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)([DW]))?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+?)S)?)?$",
      //pattern: "^P(([0-9]+Y)?([0-9]+M)?([0-9]+W)?([0-9]+D)?(T([0-9]+H)?([0-9]+M)?([0-9]+(.?[0-9]+)?S)?))?$",
    };
  };

  const anyOfSchema = () => {
    return {
      anyOf: [
        {
          properties: {
            timedPolicies: {
              type: "array",
              minItems: 1,
              uniqueItems: true,
              items: {
                $ref: "#/definitions/schemaTimedDeletePolicy",
              },
              errorMessage: {
                minItems: "No Timed Delete Policies were uploaded.",
              },
            },
          },
        },
      ],
      errorMessage: {
        anyOf: "Errors were found validating uploaded Delete Policies.",
      },
    };
  };

  const schemaTimedDeletePolicy = () => {
    return {
      type: "object",
      properties: {
        id: { $ref: "#/definitions/idSchema" },
        name: { $ref: "#/definitions/nameSchema" },
        flow: { $ref: "#/definitions/flowSchema" },
        enabled: { $ref: "#/definitions/enabledSchema" },
        afterCreate: { $ref: "#/definitions/ISO8601Duration" },
        afterComplete: { $ref: "#/definitions/ISO8601Duration" },
        minBytes: { type: ["number", "null"] },
        deleteMetadata: { type: "boolean" },
      },
      required: ["name"],
      additionalProperties: false,
      errorMessage: {
        required: "Must have required property 'name'.",
        additionalProperties: "Must NOT have additional properties.",
        properties: {
          id: "${0/name} - id is a required field.",
          name: "name is a required field.",
          enabled: "${0/name} - enabled must be true or false.",
          afterCreate: "${0/name} - afterCreate needs to be in ISO 8601 notation.",
          afterComplete: "${0/name} - afterComplete needs to be in ISO 8601 notation.",
        },
      },
    };
  };

  const deletePolicyFileSchema = {
    type: "object",
    allOf: [
      {
        type: "object",
        properties: {
          timedPolicies: {
            type: "array",
          },
        },
      },
      anyOfSchema(),
    ],
    definitions: {
      schemaTimedDeletePolicy: schemaTimedDeletePolicy(),
      ISO8601Duration: ISO8601Duration(),
      idSchema: idSchema(),
      nameSchema: nameSchema(),
      flowSchema: flowSchema(),
      enabledSchema: enabledSchema(),
      maxPercentSchema: maxPercentSchema(),
    },
    required: ["timedPolicies"],
  };

  const deletePolicySchema = {
    type: "object",
    oneOf: [{ $ref: "#/definitions/schemaTimedDeletePolicy" }],
    definitions: {
      schemaTimedDeletePolicy: schemaTimedDeletePolicy(),
      ISO8601Duration: ISO8601Duration(),
      idSchema: idSchema(),
      nameSchema: nameSchema(),
      flowSchema: flowSchema(),
      enabledSchema: enabledSchema(),
      maxPercentSchema: maxPercentSchema(),
    },
  };

  const deletePolicyValidator = ajv.compile(deletePolicySchema);

  const deletePolicyFileValidator = ajv.compile(deletePolicyFileSchema);

  const validateDeletePolicy = (uploadedDeletePolicy: any) => {
    const policyToBeValidated = JSON.parse(uploadedDeletePolicy);

    const validSingleDeletePolicy = deletePolicyValidator(policyToBeValidated);

    if (!validSingleDeletePolicy) {
      return deletePolicyValidator.errors;
    } else {
      return false;
    }
  };

  const validateDeletePolicyFile = (uploadedDeletePolicyFile: any) => {
    const policyFileToBeValidated = JSON.parse(uploadedDeletePolicyFile);

    const validDeletePolicyFile = deletePolicyFileValidator(policyFileToBeValidated);

    if (!validDeletePolicyFile) {
      return deletePolicyFileValidator.errors;
    } else {
      return false;
    }
  };

  return {
    validateDeletePolicy,
    validateDeletePolicyFile,
  };
}
