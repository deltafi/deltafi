#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

from typing import List

from deltafi.result import TransformResult, TransformResults

from .assertions import *
from .framework import TestCaseBase, ActionTest, IOContent


class TransformTestCase(TestCaseBase):
    def __init__(self, fields: Dict):
        super().__init__(fields)
        self.results = []

    def expect_transform_result(self):
        self.expected_result_type = TransformResult

    def expect_transform_results(self):
        self.expected_result_type = TransformResults

    def add_transform_result(self, content: List[IOContent], metadata: Dict, delete_metadata_keys: List[str],
                             annotations: Dict, name: str = None):
        self.results.append(
            {
                'content': content,
                'metadata': metadata,
                'delete_metadata_keys': delete_metadata_keys,
                'annotations': annotations,
                'name': name
            }
        )


class TransformActionTest(ActionTest):
    def __init__(self, package_name: str):
        """
        Provides structure for testing DeltaFi Transform action
        Args:
            package_name: name of the actions package for finding resources
        """
        super().__init__(package_name)

    def transform(self, test_case: TransformTestCase):
        if test_case.expected_result_type == TransformResult:
            self.expect_transform_result(test_case)
        elif test_case.expected_result_type == TransformResults:
            self.expect_transform_results(test_case)
        else:
            super().execute(test_case)

    def expect_transform_result(self, test_case: TransformTestCase):
        result = super().run_and_check_result_type(test_case, TransformResult)
        self.assert_transform_result(test_case, result)

    def expect_transform_results(self, test_case: TransformTestCase):
        result = super().run_and_check_result_type(test_case, TransformResults)
        self.assert_transform_results(test_case, result)

    def assert_transform_results(self, test_case: TransformTestCase, result: TransformResults):
        assert_equal_len(test_case.results, result.named_results)
        for index, named_result in enumerate(result.named_results):
            self.compare_one_transform_result(test_case, named_result.result, index)
            expected = test_case.results[index]
            if 'name' in expected:
                assert_equal_with_label(expected["name"], named_result.name, f"name[{index}]")

    def assert_transform_result(self, test_case: TransformTestCase, result: TransformResult):
        # Check metrics
        self.compare_metrics(test_case.expected_metrics, result.metrics)
        self.compare_one_transform_result(test_case, result, 0)

    def compare_one_transform_result(self, test_case: TransformTestCase, result: TransformResult, index: int):
        expected = test_case.results[index]

        # Check output
        self.compare_content_list(test_case.compare_tool, expected['content'], result.content)

        # Check metadata
        assert_keys_and_values(expected['metadata'], result.metadata)

        # Check deleted metadata
        for key in expected['delete_metadata_keys']:
            assert_key_in(key, result.delete_metadata_keys)

        # Check annotations
        assert_keys_and_values(expected['annotations'], result.annotations)
