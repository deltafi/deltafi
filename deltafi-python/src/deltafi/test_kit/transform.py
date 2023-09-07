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

from deltafi.result import TransformResult

from .assertions import *
from .framework import TestCaseBase, ActionTest


class TransformTestCase(TestCaseBase):
    def __init__(self, fields: Dict):
        super().__init__(fields)
        self.metadata = {}
        self.delete_metadata_keys = []
        self.annotations = {}

    def expect_transform_result(self, metadata: Dict, delete_metadata_keys: List[str], annotations: Dict):
        self.expected_result_type = TransformResult
        self.metadata = metadata
        self.delete_metadata_keys = delete_metadata_keys
        self.annotations = annotations


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
        else:
            super().execute(test_case)

    def expect_transform_result(self, test_case: TransformTestCase):
        result = super().run_and_check_result_type(test_case, TransformResult)
        self.assert_transform_result(test_case, result)

    def assert_transform_result(self, test_case: TransformTestCase, result: TransformResult):
        # Check output
        self.compare_all_output(test_case.compare_tool, result.content)

        # Check metadata
        assert_keys_and_values(test_case.metadata, result.metadata)

        # Check deleted metadata
        for key in test_case.delete_metadata_keys:
            assert_key_in(key, result.delete_metadata_keys)

        # Check annotations
        assert_keys_and_values(test_case.annotations, result.annotations)
