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

from deltafi.result import EnrichResult

from .assertions import *
from .framework import TestCaseBase, ActionTest


class EnrichTestCase(TestCaseBase):
    def __init__(self, fields: Dict):
        super().__init__(fields)
        self.enrichments = []
        self.annotations = {}

    def expect_enrich_result(self, annotations: Dict):
        self.expected_result_type = EnrichResult
        self.annotations = annotations

    def add_enrichment(self, name: str, value: str, media_type: str):
        self.enrichments.append({'name': name, 'value': value, 'mediaType': media_type})


class EnrichActionTest(ActionTest):
    def __init__(self, package_name: str):
        """
        Provides structure for testing DeltaFi Enrich action
        Args:
            package_name: name of the actions package for finding resources
        """
        super().__init__(package_name)

    def enrich(self, test_case: EnrichTestCase):
        if test_case.expected_result_type == EnrichResult:
            self.expect_enrich_result(test_case)
        else:
            super().execute(test_case)

    def expect_enrich_result(self, test_case: EnrichTestCase):
        result = super().run_and_check_result_type(test_case, EnrichResult)
        self.assert_enrich_result(test_case, result)

    def assert_enrich_result(self, test_case: EnrichTestCase, result: EnrichResult):
        # Check metrics
        self.compare_metrics(test_case.expected_metrics, result.metrics)

        # Check annotations
        assert_keys_and_values(test_case.annotations, result.annotations)

        # Check enrichments
        assert_equal_len(test_case.enrichments, result.enrichments)
        if len(test_case.enrichments) > 0:
            for index, expected in enumerate(test_case.enrichments):
                actual = result.enrichments[index]
                assert_equal(expected, actual)
