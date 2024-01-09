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

from deltafi.result import ValidateResult

from .assertions import *
from .framework import TestCaseBase, ActionTest


class ValidateTestCase(TestCaseBase):
    def __init__(self, fields: Dict):
        super().__init__(fields)

    def expect_validate_result(self):
        self.expected_result_type = ValidateResult


class ValidateActionTest(ActionTest):
    def __init__(self, package_name: str):
        """
        Provides structure for testing DeltaFi Validate action
        Args:
            package_name: name of the actions package for finding resources
        """
        super().__init__(package_name)

    def validate(self, test_case: ValidateTestCase):
        if test_case.expected_result_type == ValidateResult:
            self.expect_validate_result(test_case)
        else:
            super().execute(test_case)

    def expect_validate_result(self, test_case: ValidateTestCase):
        result = super().run_and_check_result_type(test_case, ValidateResult)
        self.assert_validate_result(test_case, result)

    def assert_validate_result(self, test_case: ValidateTestCase, result: ValidateResult):
        # Check metrics
        self.compare_metrics(test_case.expected_metrics, result.metrics)
