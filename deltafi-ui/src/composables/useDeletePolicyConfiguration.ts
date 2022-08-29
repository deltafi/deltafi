/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
import _ from "lodash";

export default function useDeletePolicyConfiguration() {
  const ajv = new Ajv({ allErrors: true });
  require("ajv-errors")(ajv);

  const idSchema = () => {
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

  const lockedSchema = () => {
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
      //pattern: "^P(?=\d|T\d)(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)([DW]))?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+?)S)?)?$",
      //pattern: "^P(([0-9]+Y)?([0-9]+M)?([0-9]+W)?([0-9]+D)?(T([0-9]+H)?([0-9]+M)?([0-9]+(.?[0-9]+)?S)?))?$",
    };
  };

  const schemaTimedDeletePolicy = () => {
    return {
      type: "object",
      properties: {
        id: { $ref: "#/definitions/idSchema" },
        flow: { $ref: "#/definitions/flowSchema" },
        enabled: { $ref: "#/definitions/enabledSchema" },
        locked: { $ref: "#/definitions/lockedSchema" },
        afterCreate: { $ref: "#/definitions/ISO8601Duration" },
        afterComplete: { $ref: "#/definitions/ISO8601Duration" },
        minBytes: { type: ["number", "null"] },
        deleteMetadata: { type: "boolean" },
      },
      required: ["id"],
      additionalProperties: false,
      errorMessage: {
        properties: {
          id: "${0/id} - id is a required field.",
          enabled: "${0/id} - enabled must be true or false",
          locked: "${0/id} - locked must be true or false",
          afterCreate: "${0/id} - afterCreate needs to be in ISO 8601 notation.",
          afterComplete: "${0/id} - afterComplete needs to be in ISO 8601 notation.",
        },
      },
    };
  };

  const schemaDiskSpaceDeletePolicy = () => {
    return {
      type: "object",
      properties: {
        id: { $ref: "#/definitions/idSchema" },
        flow: { $ref: "#/definitions/flowSchema" },
        enabled: { $ref: "#/definitions/enabledSchema" },
        locked: { $ref: "#/definitions/lockedSchema" },
        maxPercent: { $ref: "#/definitions/maxPercentSchema" },
      },
      required: ["id"],
      additionalProperties: false,
      errorMessage: {
        properties: {
          id: "${0/id} - id is a required field.",
          enabled: "${0/id} - enabled must be true or false",
          locked: "${0/id} - locked must be true or false",
          maxPercent: "${0/id} - maxPercent must be a number between 0 and 100.",
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
          diskSpacePolicies: {
            type: "array",
          },
        },
      },
      {
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
              },
            },
          },
          {
            properties: {
              diskSpacePolicies: {
                type: "array",
                minItems: 1,
                uniqueItems: true,
                items: {
                  $ref: "#/definitions/schemaDiskSpaceDeletePolicy",
                },
              },
            },
          },
        ],
      },
    ],
    definitions: {
      schemaTimedDeletePolicy: schemaTimedDeletePolicy(),
      schemaDiskSpaceDeletePolicy: schemaDiskSpaceDeletePolicy(),
      ISO8601Duration: ISO8601Duration(),
      idSchema: idSchema(),
      flowSchema: flowSchema(),
      enabledSchema: enabledSchema(),
      lockedSchema: lockedSchema(),
      maxPercentSchema: maxPercentSchema(),
    },
    required: ["timedPolicies", "diskSpacePolicies"],
  };

  const deletePolicySchema = {
    type: "object",
    oneOf: [{ $ref: "#/definitions/schemaDiskSpaceDeletePolicy" }, { $ref: "#/definitions/schemaTimedDeletePolicy" }],
    definitions: {
      schemaTimedDeletePolicy: schemaTimedDeletePolicy(),
      schemaDiskSpaceDeletePolicy: schemaDiskSpaceDeletePolicy(),
      ISO8601Duration: ISO8601Duration(),
      idSchema: idSchema(),
      flowSchema: flowSchema(),
      enabledSchema: enabledSchema(),
      lockedSchema: lockedSchema(),
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
