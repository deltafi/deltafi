#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

from deltafi.result import FormatResult, FormatManyResult

from .assertions import *
from .framework import TestCaseBase, ActionTest


class FormatTestCase(TestCaseBase):
    def __init__(self, fields: Dict):
        super().__init__(fields)
        self.metadata = {}
        self.delete_metadata_keys = []
        self.expected_format_many_result = []

    def expect_format_result(self, metadata: Dict, delete_metadata_keys: List[str]):
        self.expected_result_type = FormatResult
        self.metadata = metadata
        self.delete_metadata_keys = delete_metadata_keys

    def add_format_many_result(self, metadata: Dict, delete_metadata_keys: List):
        self.expected_result_type = FormatManyResult
        self.expected_format_many_result.append(
            {
                "metadata": metadata,
                "delete_metadata_keys": delete_metadata_keys
            }
        )


class FormatActionTest(ActionTest):
    def __init__(self, package_name: str):
        """
        Provides structure for testing DeltaFi Format action
        Args:
            package_name: name of the actions package for finding resources
        """
        super().__init__(package_name)

    def format(self, test_case: FormatTestCase):
        if test_case.expected_result_type == FormatManyResult:
            self.expect_format_many_result(test_case)
        elif test_case.expected_result_type == FormatResult:
            self.expect_format_result(test_case)
        else:
            super().execute(test_case)

    def expect_format_result(self, test_case: FormatTestCase):
        result = super().run_and_check_result_type(test_case, FormatResult)
        self.assert_format_result(test_case, result)

    def expect_format_many_result(self, test_case: FormatTestCase):
        result = super().run_and_check_result_type(test_case, FormatManyResult)
        self.assert_format_many_result(test_case, result)

    def assert_format_result(self, test_case: FormatTestCase, result: FormatResult):
        # Check output
        if result.content is None:
            self.compare_all_output(test_case.compare_tool, [])
        else:
            self.compare_all_output(test_case.compare_tool, [result.content])

        # Check metadata
        assert_keys_and_values(test_case.metadata, result.metadata)

        # Check deleted metadata
        for key in test_case.delete_metadata_keys:
            assert_key_in(key, result.delete_metadata_keys)

    def assert_format_many_result(self, test_case: FormatTestCase, actual: FormatManyResult):
        assert_equal_len(test_case.expected_format_many_result, actual.format_results)
        for index, expected_child_result in enumerate(test_case.expected_format_many_result):
            actual_child = actual.format_results[index]
            self.compare_one_content(test_case.compare_tool,
                                     self.expected_outputs[index],
                                     actual_child.format_result.content,
                                     index)

            assert_keys_and_values(expected_child_result['metadata'],
                                   actual_child.format_result.metadata)
            for key in expected_child_result['delete_metadata_keys']:
                assert_key_in(key, actual_child.format_result.delete_metadata_keys)
