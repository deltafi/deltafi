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
from deltafi.action import TimedIngressAction
from deltafi.domain import Context
from deltafi.result import IngressResult, IngressResultItem
from deltafi.resultmessage import LogMessage, LogSeverity
from deltafi.test_kit.framework import IOContent
from deltafi.test_kit.timed_ingress import TimedIngressTestCase, TimedIngressActionTest
from pydantic import BaseModel


class SampleTimedIngressAction(TimedIngressAction):
    def __init__(self):
        super().__init__('TTimed Ingress action for testing')

    def make_item(self, context: Context, name: str, index: int):
        ingress_item = IngressResultItem(context, name)
        ingress_item.add_metadata("index", str(index))
        ingress_item.annotate("a", "b")
        if name == "even":
            ingress_item.save_string_content("even-data", "even.0", "text/plain")
        else:
            ingress_item.save_string_content("odd-data-a", "odd.1", "text/plain")
            ingress_item.save_string_content("odd-data-b", "odd.2", "text/plain")
        return ingress_item

    def ingress(self, context: Context, params: BaseModel):
        index = 0
        if context.memo is not None:
            index = 1 + int(context.memo)

        ingress_result = IngressResult(context)

        ingress_result.add_item(self.make_item(context, "even", index))
        if index % 2 == 1:
            index += 1
            ingress_result.add_item(self.make_item(context, "odd", index))

        ingress_result.memo = str(index)
        ingress_result.execute_immediate = False
        ingress_result.status_message = "success"
        ingress_result.log_info("my info message")
        return ingress_result


class SampleTimedIngressActionTest(TimedIngressActionTest):
    def __init__(self):
        super().__init__("test")
        self.action = SampleTimedIngressAction()

    def ingress_result(self,
                       two_results: bool = False,
                       skip_odd: bool = False,
                       memo_override: str = "no",
                       message: str = "success"):
        memo = None
        expected_memo = "0"
        expected_meta_val = "0"
        if two_results:
            memo = "0"
            expected_memo = "2"
            expected_meta_val = "1"

        if memo_override != "no":
            expected_memo = memo_override

        fields = {
            "data_dir": "none",
            "did": "111",
            "action": self.action,
            "in_memo": memo
        }

        even_outputs = [IOContent(
            file_name="even.0",
            content_bytes="even-data",
            content_type="text/plain")]

        odd_outputs = [
            IOContent(
                file_name="odd.1",
                content_bytes="odd-data-a",
                content_type="text/plain"),
            IOContent(
                file_name="odd.2",
                content_bytes="odd-data-b",
                content_type="text/plain")
        ]

        test_case = TimedIngressTestCase(fields)
        test_case.expect_ingress_result(
            memo=expected_memo,
            status_message=message)
        test_case.add_ingress_result_item(
            name="even",
            content=even_outputs,
            metadata={"index": expected_meta_val},
            annotations={"a": "b"})
        test_case.add_message(LogMessage.info('source', 'my info message'))
        if two_results and not skip_odd:
            test_case.add_ingress_result_item(
                name="odd",
                content=odd_outputs,
                metadata={"index": "2"})

        self.ingress(test_case)
        if two_results:
            self.has_saved_content__size(3)
        else:
            self.has_saved_content__size(1)


def test_ingress_result():
    action_test = SampleTimedIngressActionTest()
    action_test.ingress_result()


def test_ingress_result_with_memo():
    action_test = SampleTimedIngressActionTest()
    action_test.ingress_result(two_results=True)


def test_ingress_result_wrong_memo():
    action_test = SampleTimedIngressActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.ingress_result(memo_override="wrong")
    assert "invalid memo. E:wrong, A::0" in str(exc_info.value)


def test_ingress_result_item_count_mismatch():
    action_test = SampleTimedIngressActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.ingress_result(two_results=True, skip_odd=True)
    assert "item count mismatch. 1 != 2" in str(exc_info.value)


def test_ingress_result_wrong_message():
    action_test = SampleTimedIngressActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.ingress_result(message="wrong")
    assert "invalid status_message. Expected:\n<<wrong>>\nBut was:\n<<success>>" in str(exc_info.value)
