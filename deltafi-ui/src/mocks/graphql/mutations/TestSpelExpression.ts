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
// ABOUTME: Mock handler for TestSpelExpression mutation.
// ABOUTME: Evaluates expressions against test data and returns results.

export default (req: any) => {
  const { expression, mode, testData } = req.variables;

  // Check for invalid expressions
  if (expression.includes("invalidMethod")) {
    return {
      testSpelExpression: {
        valid: false,
        result: null,
        errorMessage: "Method 'invalidMethod' not found",
        errorPosition: expression.indexOf("invalidMethod"),
      },
    };
  }

  if (mode === "CONDITION") {
    // Simulate condition evaluation
    let result = "true";

    // Simple mock logic for common patterns
    if (expression.includes("metadata['type'] == 'order'")) {
      const metadata = testData?.metadata || [];
      const typeEntry = metadata.find((m: any) => m.key === "type");
      result = typeEntry?.value === "order" ? "true" : "false";
    } else if (expression.includes("hasMediaType")) {
      const match = expression.match(/hasMediaType\(['"]([^'"]+)['"]\)/);
      const mediaType = match?.[1];
      const content = testData?.content || [];
      const hasType = content.some((c: any) => c.mediaType === mediaType);
      result = hasType ? "true" : "false";
    } else if (expression.includes("content.size()")) {
      const content = testData?.content || [];
      result = String(content.length);
    }

    return {
      testSpelExpression: {
        valid: true,
        result,
        errorMessage: null,
        errorPosition: null,
      },
    };
  } else {
    // TEMPLATE mode - substitute values
    let result = expression;

    const metadata = testData?.metadata || [];
    for (const { key, value } of metadata) {
      result = result.replace(new RegExp(`\\{\\{\\s*metadata\\['${key}'\\]\\s*\\}\\}`, "g"), value);
    }

    result = result.replace(/\{\{\s*deltaFileName\s*\}\}/g, testData?.deltaFileName || "test-file.dat");
    result = result.replace(/\{\{\s*did\s*\}\}/g, testData?.did || "00000000-0000-0000-0000-000000000000");
    result = result.replace(/\{\{\s*now\(\)\s*\}\}/g, new Date().toISOString());

    return {
      testSpelExpression: {
        valid: true,
        result,
        errorMessage: null,
        errorPosition: null,
      },
    };
  }
};
