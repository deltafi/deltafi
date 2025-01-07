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
import addFormats from "ajv-formats";

export default function useAutoResumeConfiguration() {
  const ajv = new Ajv({ allErrors: true, strictRequired: false });
  require("ajv-errors")(ajv);
  addFormats(ajv);

  const autoResumeSchema = {
    type: "object",
    properties: {
      id: {
        type: ["string", "null"],
      },
      name: {
        type: ["string", "null"],
      },
      errorSubstring: {
        type: ["string", "null"],
        minLength: 1,
      },
      action: {
        type: ["string", "null"],
        minLength: 1,
      },
      maxAttempts: {
        type: ["number", "null"],
        minimum: 2,
      },
      priority: {
        type: ["number", "null"],
      },
      backOff: {
        type: "object",
        properties: {
          random: {
            type: ["boolean", "null"],
          },
          delay: {
            type: ["number", "null"],
            minimum: 0,
          },
          maxDelay: {
            type: ["number", "null"],
            minimum: 0,
          },
          multiplier: {
            type: ["number", "null"],
            minimum: 0,
          },
        },
        required: ["delay"],
        if: {
          properties: {
            random: {
              const: true,
            },
          },
          required: ["random"],
        },
        then: {
          required: ["maxDelay"],
        },
      },
    },
    required: ["name", "maxAttempts", "backOff"],
    allOf: [
      {
        type: "object",
        properties: {
          dataSource: {
            type: ["string", "null"],
            minLength: 1,
          },
          errorSubstring: {
            type: ["string", "null"],
            minLength: 1,
          },
          action: {
            type: ["string", "null"],
            minLength: 1,
          },
        },
      },
      {
        anyOf: [
          {
            required: ["dataSource"],
          },
          {
            required: ["errorSubstring"],
          },
          {
            required: ["action"],
          },
        ],
      },
    ],
  };

  const autoResumeFileSchema = {
    type: "array",
    minItems: 1,
    uniqueItems: true,
    items: {
      $ref: "#/definitions/schemaAutoResumeRule",
    },
    definitions: {
      schemaAutoResumeRule: autoResumeSchema,
    },
  };

  const autoResumeValidator = ajv.compile(autoResumeSchema);

  const autoResumeFileValidator = ajv.compile(autoResumeFileSchema);

  const validateAutoResumeRule = (uploadedAutoResumeRule: any) => {
    const ruleToBeValidated = JSON.parse(uploadedAutoResumeRule);

    const validSingleAutoResumeRule = autoResumeValidator(ruleToBeValidated);

    if (!validSingleAutoResumeRule) {
      return autoResumeValidator.errors;
    } else {
      return false;
    }
  };

  const validateAutoResumeFile = (uploadedAutoResumeFile: any) => {
    const ruleFileToBeValidated = JSON.parse(uploadedAutoResumeFile);

    const validAutoResumeFile = autoResumeFileValidator(ruleFileToBeValidated);

    if (!validAutoResumeFile) {
      return autoResumeFileValidator.errors;
    } else {
      return false;
    }
  };

  return {
    autoResumeSchema,
    validateAutoResumeRule,
    validateAutoResumeFile,
  };
}
