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

export default function useMetadataConfiguration() {
  const ajv = new Ajv({ allErrors: true });
  require("ajv-errors")(ajv);

  const flowSchema = () => {
    return {
      type: ["string"],
    };
  };

  const metadataSchema = () => {
    return {
      type: ["object"],
    };
  };

  const metadataSchemaFile = {
    type: "object",
    properties: {
      flow: { $ref: "#/definitions/flowSchema" },
      metadata: { $ref: "#/definitions/metadataSchema" },
    },
    required: ["metadata"],
    additionalProperties: false,
    definitions: {
      flowSchema: flowSchema(),
      metadataSchema: metadataSchema(),
    },
    errorMessage: {
      additionalProperties: "Must NOT have additional properties.",
      properties: {
        metadata: "metadata is required field of type key:value pairs.",
      },
    },
  };

  const metadataSchemaFileValidator = ajv.compile(metadataSchemaFile);

  const validateMetadataFile = (uploadedMetadataFile: any) => {
    const metadataFileToBeValidated = JSON.parse(uploadedMetadataFile);

    const validMetadataFile = metadataSchemaFileValidator(metadataFileToBeValidated);

    if (!validMetadataFile) {
      return metadataSchemaFileValidator.errors;
    } else {
      return false;
    }
  };

  return {
    validateMetadataFile,
  };
}
