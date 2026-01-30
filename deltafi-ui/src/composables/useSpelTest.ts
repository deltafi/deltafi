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
// ABOUTME: Composable for testing and validating SpEL expressions via GraphQL.
// ABOUTME: Provides methods to validate syntax and test expressions against sample data.

import useGraphQL from "./useGraphQL";

export type SpelExpressionMode = 'CONDITION' | 'TEMPLATE';

export type SpelContentInput = {
  name: string;
  mediaType: string;
  size: number;
};

export type SpelTestDataInput = {
  metadata?: Array<{ key: string; value: string }>;
  content?: SpelContentInput[];
  deltaFileName?: string;
  did?: string;
};

export type SpelTestResult = {
  valid: boolean;
  result?: string;
  errorMessage?: string;
  errorPosition?: number;
};

const testExpressionMutation = `
  mutation TestSpelExpression($expression: String!, $mode: SpelExpressionMode!, $testData: SpelTestDataInput) {
    testSpelExpression(expression: $expression, mode: $mode, testData: $testData) {
      valid
      result
      errorMessage
      errorPosition
    }
  }
`;

const validateExpressionMutation = `
  mutation ValidateSpelExpression($expression: String!, $mode: SpelExpressionMode!) {
    validateSpelExpression(expression: $expression, mode: $mode) {
      valid
      errorMessage
      errorPosition
    }
  }
`;

export default function useSpelTest() {
  const { response, queryGraphQLWithVariables, loading, errors } = useGraphQL();

  const testExpression = async (
    expression: string,
    mode: SpelExpressionMode,
    testData?: SpelTestDataInput
  ): Promise<SpelTestResult> => {
    await queryGraphQLWithVariables(
      testExpressionMutation,
      { expression, mode, testData },
      "TestSpelExpression"
    );

    return response.value.data?.testSpelExpression;
  };

  const validateExpression = async (
    expression: string,
    mode: SpelExpressionMode
  ): Promise<SpelTestResult> => {
    await queryGraphQLWithVariables(
      validateExpressionMutation,
      { expression, mode },
      "ValidateSpelExpression"
    );

    return response.value.data?.validateSpelExpression;
  };

  return {
    testExpression,
    validateExpression,
    loading,
    errors
  };
}
