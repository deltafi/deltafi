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


import pytest
from deltafi.action import EgressAction
from deltafi.domain import Context
from deltafi.input import EgressInput
from deltafi.metric import Metric
from deltafi.result import EgressResult, ErrorResult, FilterResult
from deltafi.resultmessage import LogMessage, LogSeverity
from deltafi.test_kit.egress import EgressActionTest, EgressTestCase
from deltafi.test_kit.framework import IOContent
from pydantic import BaseModel


class SampleEgressAction(EgressAction):
    def __init__(self):
        super().__init__('Egress action for testing')

    def egress(self, context: Context, params: BaseModel, egress_input: EgressInput):
        data = egress_input.content.load_str()
        if 'error' in data:
            return ErrorResult(context, 'Failed to egress', 'error in content')
        elif 'filter' in data:
            return FilterResult(context, "Filtered")

        return (EgressResult(context)
                .add_metric(Metric(name="mKey", value=100))
                .log_info("my random message")
                .log_warning("my warning message"))


class SampleEgressActionTest(EgressActionTest):
    def __init__(self):
        super().__init__("test")
        self.action = SampleEgressAction()

    def egress_result(self, data: str, metric_key: str = "mKey", metric_val: int = 100):
        test_case = EgressTestCase(self.get_fields(data))
        test_case.expect_egress_result()
        test_case.add_metric(Metric(name=metric_key, value=metric_val))
        test_case.add_message(LogMessage.info('source', 'my .* message'))
        test_case.add_message(LogMessage.warning('source', 'my warning message'))
        if metric_key == "add_bogus":
            test_case.add_metric(Metric(name="bogus", value=1))
        self.egress(test_case)
        self.has_saved_content__size(0)

    def filter_result(self, data: str):
        test_case = EgressTestCase(self.get_fields(data))
        test_case.expect_filter_result("Filtered")
        self.egress(test_case)
        self.has_saved_content__size(0)

    def error_result(self, data: str):
        test_case = EgressTestCase(self.get_fields(data))
        test_case.expect_error_result("Failed to egress", "error in content")
        self.egress(test_case)
        self.has_saved_content__size(0)

    def get_fields(self, data: str):
        fields = {
            "data_dir": "none",
            "action": self.action,
            "inputs": [IOContent(file_name="input.txt", content_bytes=data)]
        }
        return fields


def test_egress_result():
    action_test = SampleEgressActionTest()
    action_test.egress_result("data")


def test_egress_result_bad_metric_value():
    action_test = SampleEgressActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.egress_result("data", metric_val=0)
    assert "invalid metric value for mKey. E:0, A::100" in str(exc_info.value)


def test_egress_result_bad_metric_name():
    action_test = SampleEgressActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.egress_result("data", metric_key="wrong")
    assert "invalid metric name. E:wrong, A::mKey" in str(exc_info.value)


def test_egress_result_wrong_metrics_count():
    action_test = SampleEgressActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.egress_result("data", metric_key="add_bogus")
    assert "invalid metrics count. 2 != 1" in str(exc_info.value)


def test_not_an_egress_result():
    action_test = SampleEgressActionTest()
    with pytest.raises(ValueError) as exc_info:
        action_test.egress_result("error")
    assert "ErrorResult does not match EgressResult" in str(exc_info.value)


def test_filter_result():
    action_test = SampleEgressActionTest()
    action_test.filter_result("filter")


def test_not_a_filter_result():
    action_test = SampleEgressActionTest()
    with pytest.raises(ValueError) as exc_info:
        action_test.filter_result("error")
    assert "ErrorResult does not match FilterResult" in str(exc_info.value)


def test_error_result():
    action_test = SampleEgressActionTest()
    action_test.error_result("error")


def test_not_an_error_result():
    action_test = SampleEgressActionTest()
    with pytest.raises(ValueError) as exc_info:
        action_test.error_result("data")
    assert "EgressResult does not match ErrorResult" in str(exc_info.value)
