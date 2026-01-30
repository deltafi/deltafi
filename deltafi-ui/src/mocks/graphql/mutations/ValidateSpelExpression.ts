/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
// ABOUTME: Mock handler for ValidateSpelExpression mutation.
// ABOUTME: Returns validation results based on expression syntax.

export default (req: any) => {
  const { expression, mode } = req.variables;

  // Simulate validation errors for known bad patterns
  if (expression.includes("invalidMethod")) {
    return {
      validateSpelExpression: {
        valid: false,
        errorMessage: "Method 'invalidMethod' not found",
        errorPosition: expression.indexOf("invalidMethod"),
      },
    };
  }

  // Check for unclosed brackets
  if (expression.includes("[") && !expression.includes("]")) {
    return {
      validateSpelExpression: {
        valid: false,
        errorMessage: "Missing closing bracket",
        errorPosition: expression.indexOf("["),
      },
    };
  }

  // Check for unclosed template expressions
  if (mode === "TEMPLATE" && expression.includes("{{") && !expression.includes("}}")) {
    return {
      validateSpelExpression: {
        valid: false,
        errorMessage: "Missing closing braces",
        errorPosition: expression.indexOf("{{"),
      },
    };
  }

  // Valid expression
  return {
    validateSpelExpression: {
      valid: true,
      errorMessage: null,
      errorPosition: null,
    },
  };
};
