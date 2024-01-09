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

from deltafi.result import LoadResult, ReinjectResult

from .assertions import *
from .compare_helpers import GenericCompareHelper, CompareHelper
from .framework import TestCaseBase, ActionTest, IOContent


class LoadTestCase(TestCaseBase):
    def __init__(self, fields: Dict):
        super().__init__(fields)
        self.metadata = {}
        self.delete_metadata_keys = []
        self.annotations = {}
        self.domains = []
        self.domain_cmp_tool = GenericCompareHelper()
        self.children = []

    def set_domain_compare_tool(self, helper: CompareHelper):
        self.domain_cmp_tool = helper

    def expect_load_result(self, metadata: Dict, delete_metadata_keys: List[str], annotations: Dict, domains: List):
        self.expected_result_type = LoadResult
        self.metadata = metadata
        self.delete_metadata_keys = delete_metadata_keys
        self.annotations = annotations
        self.domains = domains

    def add_reinject_child(self, filename: str, flow: str, content: IOContent, metadata: Dict):
        self.expected_result_type = ReinjectResult
        self.children.append(
            {
                "filename": filename,
                "flow": flow,
                "content": content,
                "metadata": metadata
            }
        )


class LoadActionTest(ActionTest):
    def __init__(self, package_name: str):
        """
        Provides structure for testing DeltaFi Load action
        Args:
            package_name: name of the actions package for finding resources
        """
        super().__init__(package_name)

    def load(self, test_case: LoadTestCase):
        if test_case.expected_result_type == ReinjectResult:
            self.expect_reinject_result(test_case)
        elif test_case.expected_result_type == LoadResult:
            self.expect_load_result(test_case)
        else:
            super().execute(test_case)

    def expect_load_result(self, test_case: LoadTestCase):
        result = super().run_and_check_result_type(test_case, LoadResult)
        self.assert_load_result(test_case, result)

    def expect_reinject_result(self, test_case: LoadTestCase):
        result = super().run_and_check_result_type(test_case, ReinjectResult)
        self.assert_reinject_result(test_case, result)

    def assert_load_result(self, test_case: LoadTestCase, result: LoadResult):
        # Check metrics
        self.compare_metrics(test_case.expected_metrics, result.metrics)

        # Check output
        self.compare_all_output(test_case.compare_tool, result.content)

        # Check metadata
        assert_keys_and_values(test_case.metadata, result.metadata)

        # Check deleted metadata
        for key in test_case.delete_metadata_keys:
            assert_key_in(key, result.delete_metadata_keys)

        # Check annotations
        assert_keys_and_values(test_case.annotations, result.annotations)

        # Check domains
        self.compare_domains(test_case.domain_cmp_tool, test_case.domains, result.domains)

    def assert_reinject_result(self, test_case: LoadTestCase, actual: ReinjectResult):
        # Check metrics
        self.compare_metrics(test_case.expected_metrics, actual.metrics)

        # Check reinject
        assert_equal_len(test_case.children, actual.children)
        for index, expected in enumerate(test_case.children):
            reinject_child = actual.children[index]
            assert_equal(expected['filename'], reinject_child.filename)
            assert_equal(expected['flow'], reinject_child.flow)
            assert_keys_and_values(expected['metadata'], reinject_child.metadata)

            expected_value = expected['content']
            child_content = reinject_child.content[0]
            seg_id = child_content.segments[0].uuid
            actual_content = self.content_service.get_output(seg_id)

            if type(expected_value) == str:
                test_case.domain_cmp_tool.compare(expected_value, actual_content, f"RI_child[{index}]")
            elif type(expected_value) == IOContent:
                expected_data = self.load_file(expected_value)
                test_case.domain_cmp_tool.compare(expected_data, actual_content, f"RI_child[{index}]")
            else:
                raise ValueError(f"unknown expected_value type: {type(expected_value)}")
