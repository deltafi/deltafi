#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

from deltafi.result import IngressResult, IngressResultItem, IngressStatusEnum

from .assertions import *
from .framework import TestCaseBase, ActionTest, IOContent


class TimedIngressTestCase(TestCaseBase):
    def __init__(self, fields: Dict):
        super().__init__(fields)
        self.memo = None
        self.results = []
        self.execute_immediate = False
        self.status = IngressStatusEnum.HEALTHY
        self.status_message = None

    def expect_ingress_result(self,
                              memo: str = None,
                              exec_immed: bool = False,
                              status: IngressStatusEnum = IngressStatusEnum.HEALTHY,
                              status_message: str = None):
        self.expected_result_type = IngressResult
        self.memo = memo
        self.execute_immediate = exec_immed
        self.status = status
        self.status_message = status_message

    def add_ingress_result_item(self, content: List[IOContent], metadata: Dict, name: str = None,
                                annotations: Dict = None):
        if annotations is None:
            annotations = {}
        self.results.append(
            {
                'content': content,
                'metadata': metadata,
                'name': name,
                'annotations': annotations
            }
        )


class TimedIngressActionTest(ActionTest):
    def __init__(self, package_name: str):
        """
        Provides structure for testing DeltaFi TimedIngress action
        Args:
            package_name: name of the actions package for finding resources
        """
        super().__init__(package_name)

    def ingress(self, test_case: TimedIngressTestCase):
        if test_case.expected_result_type == IngressResult:
            self.expect_ingress_result(test_case)
        else:
            super().execute(test_case)

    def expect_ingress_result(self, test_case: TimedIngressTestCase):
        result = super().run_and_check_result_type(test_case, IngressResult)
        self.assert_ingress_result(test_case, result)

    def assert_ingress_result(self, test_case: TimedIngressTestCase, result: IngressResult):
        assert_equal_short(test_case.memo, result.memo, "invalid memo")
        assert_equal_short(test_case.execute_immediate, result.execute_immediate, "invalid execute_immediate")
        assert_equal_short(test_case.status, result.status, "invalid status")
        assert_equal_with_label(test_case.status_message, result.status_message, "invalid status_message")
        self.compare_log_messages(test_case.expected_messages, result.messages)

        assert_equal_len_with_label(test_case.results, result.ingress_result_items, "item count mismatch")
        for index, ingress_item in enumerate(result.ingress_result_items):
            self.compare_one_ingress_item(test_case, ingress_item, index)
            expected = test_case.results[index]
            if 'name' in expected:
                assert_equal_with_label(expected["name"], ingress_item.delta_file_name, f"name[{index}]")

    def compare_one_ingress_item(self, test_case: TimedIngressTestCase, result: IngressResultItem, index: int):
        expected = test_case.results[index]

        # Check output
        self.compare_content_list(test_case.compare_tool, expected['content'], result.content)

        # Check metadata
        assert_keys_and_values(expected['metadata'], result.metadata)

        # Check annotations
        assert_keys_and_values(expected['annotations'], result.annotations)
