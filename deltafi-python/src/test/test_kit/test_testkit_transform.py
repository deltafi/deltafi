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
from deltafi.action import TransformAction
from deltafi.domain import Context
from deltafi.input import TransformInput
from deltafi.result import TransformResult, TransformResults, ChildTransformResult, ErrorResult, FilterResult
from deltafi.resultmessage import LogMessage, LogSeverity
from deltafi.test_kit.framework import IOContent
from deltafi.test_kit.transform import TransformTestCase, TransformActionTest
from pydantic import BaseModel, Field


class SampleTransformParams(BaseModel):
    mode: str = Field(description="Choose the action operation mode")


class SampleTransformAction(TransformAction):
    def __init__(self):
        super().__init__('Transform action for testing')

    def param_class(self):
        return SampleTransformParams

    def transform(self, context: Context, params: SampleTransformParams, transform_input: TransformInput):
        # Creates orphaned content:
        TransformResult(context).save_string_content("123", "temp1.txt", "text/plain")
        if params.mode == "error":
            return ErrorResult(context, 'Error cause', 'error context')
        elif params.mode == "filter":
            return FilterResult(context, "Filter cause")
        else:
            data = f"{transform_input.first_content().load_str()}Output"
            if "meta" in params.mode:
                val = f"valueOut.{context.did}"
                return (TransformResult(context)
                        .add_metadata("keyOut", val)
                        .save_string_content(data, "output.txt", "text/plain"))
            elif "annotate" in params.mode:
                val = transform_input.get_metadata("keyIn")
                return (TransformResult(context)
                        .annotate("keyOut", val)
                        .delete_metadata_key("keyIn")
                        .save_string_content(data, "output.txt", "text/plain"))
            elif "many" in params.mode:
                transform_many_result = TransformResults(context)
                this_content = self.save_content("this", context)
                that_content = self.save_content("that", context)

                this_child = (ChildTransformResult(context, "this.many")
                              .add_content(this_content)
                              .add_metadata("key", "THIS"))
                that_child = (ChildTransformResult(context, "that.many")
                              .add_content(that_content)
                              .add_metadata("key", "THAT"))

                transform_many_result.add_result(this_child)
                transform_many_result.add_result(that_child)
                return transform_many_result
            elif "warning" in params.mode:
                return (TransformResult(context)
                        .save_string_content(data, "output.txt", "text/plain")
                        .log_warning("my warning message"))
            else:
                return (TransformResult(context)
                        .save_string_content(data, "output.txt", "text/plain"))

    def save_content(self, data: str, context):
        result = (TransformResult(context)
                  .save_string_content(data, f"{data}.txt", 'text/plain'))
        return result.content[0]


class SampleTransformActionTest(TransformActionTest):
    def __init__(self):
        super().__init__("test")
        self.action = SampleTransformAction()

    def transform_result(self,
                         param_mode: str = "transform",
                         output_name: str = "output.txt",
                         output_data: str = "InputOutput",
                         media_type: str = "text/plain",
                         del_meta_keys: list[str] = [],
                         meta_out: dict[str, str] = {},
                         annotations_out: dict[str, str] = {}):
        fields = {
            "data_dir": "none",
            "did": "abc123",
            "action": self.action,
            "file_name": "input.txt",
            "in_meta": {
                "keyIn": "valueIn"
            },
            "inputs": [
                IOContent(file_name="input.txt", content_bytes="Input")
            ],
            "parameters": {
                "mode": param_mode
            }
        }

        output = IOContent(
            file_name=output_name,
            content_bytes=output_data,
            content_type=media_type)

        test_case = TransformTestCase(fields)
        test_case.expect_transform_result()
        test_case.add_transform_result(
            content=[output],
            annotations=annotations_out,
            metadata=meta_out,
            delete_metadata_keys=del_meta_keys)
        if param_mode == 'warning':
            test_case.add_message(LogMessage.warning('source', 'my warning message'))
        self.transform(test_case)
        self.has_saved_content__size(2)

    def transform_many_result(self,
                              param_mode: str = "many",
                              num_outputs: int = 2):
        fields = {
            "data_dir": "none",
            "action": self.action,
            "file_name": "input.txt",
            "in_meta": {
                "keyIn": "valueIn"
            },
            "inputs": [
                IOContent(file_name="input.txt", content_bytes="Input")
            ],
            "parameters": {
                "mode": param_mode
            }
        }

        test_case = TransformTestCase(fields)
        test_case.expect_transform_results()
        test_case.add_transform_result(
            content=[IOContent(file_name="this.txt", content_bytes="this", content_type="text/plain")],
            annotations={},
            metadata={"key": "THIS"},
            delete_metadata_keys=[],
            name="this.many")

        if num_outputs == 2:
            test_case.add_transform_result(
                content=[IOContent(file_name="that.txt", content_bytes="that", content_type="text/plain")],
                annotations={},
                metadata={"key": "THAT"},
                delete_metadata_keys=[],
                name="that.many")

        self.transform(test_case)
        self.has_saved_content__size(3)

    def filter_result(self, filter_cause: str):
        fields = {
            "data_dir": "none",
            "did": "222",
            "action": self.action,
            "file_name": "input.txt",
            "inputs": [
                IOContent(file_name="input.txt", content_bytes="Input")
            ],
            "parameters": {
                "mode": "filter"
            }
        }

        test_case = TransformTestCase(fields)
        test_case.expect_filter_result(
            cause=filter_cause)
        self.transform(test_case)
        self.has_saved_content__size(1)

    def error_result(self, error_cause: str, error_context: str):
        fields = {
            "data_dir": "none",
            "action": self.action,
            "file_name": "input.txt",
            "inputs": [
                IOContent(file_name="input.txt", content_bytes="Input")
            ],
            "parameters": {
                "mode": "error"
            }
        }

        test_case = TransformTestCase(fields)
        test_case.expect_error_result(
            cause=error_cause,
            context=error_context)
        self.transform(test_case)
        self.has_saved_content__size(1)


def test_good_transform_many_result():
    action_test = SampleTransformActionTest()
    action_test.transform_many_result()


def test_not_a_transform_many_result():
    action_test = SampleTransformActionTest()
    with pytest.raises(ValueError) as exc_info:
        action_test.transform_many_result(param_mode="error")
    assert "ErrorResult does not match TransformResults" in str(exc_info.value)


def test_transform_many_result_missing_child():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.transform_many_result(num_outputs=1)
    assert "invalid child count. 1 != 2" in str(exc_info.value)


def test_good_transform_result():
    action_test = SampleTransformActionTest()
    action_test.transform_result()


def test_good_transform_result_with_warning():
    action_test = SampleTransformActionTest()
    action_test.transform_result(param_mode='warning')


def test_not_a_transform_result():
    action_test = SampleTransformActionTest()
    with pytest.raises(ValueError) as exc_info:
        action_test.transform_result(param_mode="error")
    assert "ErrorResult does not match TransformResult" in str(exc_info.value)


def test_transform_result_wrong_output():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.transform_result(output_data="Wrong")
    assert "Content[0]. Expected:\n<<Wrong>>\nBut was:\n<<InputOutput>>" in str(exc_info.value)


def test_transform_result_wrong_media_type():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.transform_result(media_type="app/xml")
    assert "app/xml != text/plain" in str(exc_info.value)


def test_transform_result_wrong_name():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.transform_result(output_name="file.xml")
    assert "file.xml != output.txt" in str(exc_info.value)


def test_good_transform_result_with_metadata():
    action_test = SampleTransformActionTest()
    action_test.transform_result(param_mode="meta", meta_out={"keyOut": "valueOut.abc123"})


def test_transform_result_missing_metadata():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.transform_result(meta_out={"keyOut": "valueOut.abc123"})
    assert "keyOut not found" in str(exc_info.value)


def test_transform_result_wrong_meta_key():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.transform_result(param_mode="meta", meta_out={"keyWrong": "valueOut.abc123"})
    assert "keyWrong not found" in str(exc_info.value)


def test_transform_result_too_much_metadata():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.transform_result(param_mode="meta",
                                     meta_out={"keyOut": "valueOut.abc123", "this": "that"})
    assert "this not found" in str(exc_info.value)


def test_transform_result_wrong_meta_value():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.transform_result(param_mode="meta", meta_out={"keyOut": "wrong"})
    assert "invalid value for key keyOut. E:wrong, A::valueOut.abc123" in str(exc_info.value)


def test_good_transform_result_with_annotations():
    action_test = SampleTransformActionTest()
    action_test.transform_result(param_mode="annotate",
                                 del_meta_keys=["keyIn"],
                                 annotations_out={"keyOut": "valueIn"})


def test_transform_result_ignore_dict_value():
    action_test = SampleTransformActionTest()
    action_test.transform_result(param_mode="annotate",
                                 del_meta_keys=["keyIn"],
                                 annotations_out={"keyOut": "%%IGNORE%%"})


def test_transform_result_missing_annotations():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.transform_result(param_mode="annotate",
                                     del_meta_keys=["keyIn"],
                                     annotations_out={"wrongKey": "valueIn"})
    assert "wrongKey not found" in str(exc_info.value)


def test_transform_result_del_meta_keys_error():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.transform_result(param_mode="annotate",
                                     del_meta_keys=["key1", "key2"],
                                     annotations_out={"keyOut": "valueIn"})
    assert "key1 not found" in str(exc_info.value)


def test_good_filter_result():
    action_test = SampleTransformActionTest()
    action_test.filter_result("Filter cause")


def test_filter_result_wrong_cause():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.filter_result("wrong")
    assert "filter cause. Expected:\n<<wrong>>\nBut was:\n<<Filter cause>>" in str(exc_info.value)


def test_good_error_result():
    action_test = SampleTransformActionTest()
    action_test.error_result("Error cause", "error context")


def test_error_result_wrong_cause():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.error_result("wrong", "error context")
    assert "error cause. Expected:\n<<wrong>>\nBut was:\n<<Error cause>>" in str(exc_info.value)


def test_error_result_wrong_context():
    action_test = SampleTransformActionTest()
    with pytest.raises(AssertionError) as exc_info:
        action_test.error_result("Error cause", "wrong")
    assert "error context. Expected:\n<<wrong>>\nBut was:\n<<error context>>" in str(exc_info.value)
