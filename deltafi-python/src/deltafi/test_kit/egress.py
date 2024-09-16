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

from deltafi.result import EgressResult

from .assertions import *
from .framework import TestCaseBase, ActionTest


class EgressTestCase(TestCaseBase):
    def __init__(self, fields: Dict):
        super().__init__(fields)

    def expect_egress_result(self):
        self.expected_result_type = EgressResult


class EgressActionTest(ActionTest):
    def __init__(self, package_name: str):
        """
        Provides structure for testing DeltaFi Egress action
        Args:
            package_name: name of the actions package for finding resources
        """
        super().__init__(package_name)

    def egress(self, test_case: EgressTestCase):
        if test_case.expected_result_type == EgressResult:
            self.expect_egress_result(test_case)
        else:
            super().execute(test_case)

    def expect_egress_result(self, test_case: EgressTestCase):
        result = super().run_and_check_result_type(test_case, EgressResult)
        self.assert_egress_result(test_case, result)

    def assert_egress_result(self, test_case: EgressTestCase, result: EgressResult):
        # Check metrics
        self.compare_metrics(test_case.expected_metrics, result.metrics)
