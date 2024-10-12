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

import uuid
from abc import ABC
from importlib.resources import files
from typing import List

from deltafi.domain import DeltaFileMessage, Event, Content, Context
from deltafi.logger import get_logger
from deltafi.metric import Metric
from deltafi.result import ErrorResult, FilterResult
from deltafi.storage import Segment
from .assertions import *
from .compare_helpers import GenericCompareHelper, CompareHelper
from .constants import *


class IOContent:
    """
    The IOContent class holds the details for loading input or output
     content into the test framework.
    Attributes:
        file_name (str)    : The name of file in test/data.
        content_name (str) : The name of the content.
        content_type (str) : The media type of the content
        offset (int)       : Offset to use in Segment
        content_bytes (str): Optional.  If set to a String of length greater than zero, indicates to consumers of this
                             IOContent that they should bypass file read and use these bytes for content.
        no_content (bool)  : Optional.  If 'True', then consumers should not attempt to interpret content but should
                             apply other aspects of this IOContent.  When 'True', 'content_bytes' should be ignored and
                             loaded content, if any, should be interpreted as empty String or otherwise as documented by
                             the consumer.
    """

    def __init__(self, file_name: str, content_name: str = None, content_type: str = None, offset: int = 0,
                 content_bytes: str = "", no_content: bool = False):
        self.file_name = file_name
        if content_name is None:
            self.content_name = file_name
        else:
            self.content_name = content_name
        if content_type is None:
            self.content_type = IOContent.file_type(file_name)
        else:
            self.content_type = content_type
        self.offset = offset
        self.no_content = no_content
        if no_content:
            self.content_bytes = None
        else:
            self.content_bytes = content_bytes
        self.segment_uuid = uuid.uuid4()

    @classmethod
    def file_type(cls, name: str):
        if name.endswith(".json"):
            return "application/json"
        elif name.endswith(".xml"):
            return "application/xnl"
        elif name.endswith(".txt"):
            return "text/plain"
        else:
            return "application/octet-stream"


class LoadedContent:
    def __init__(self, did: str, ioc: IOContent, data: str):
        self.name = ioc.content_name
        self.content_type = ioc.content_type
        self.offset = ioc.offset
        if data is not None:
            self.data = data
        else:
            if ioc.no_content:
                self.data = ""
            else:
                self.data = ioc.content_bytes
        self.segment = Segment.from_dict(
            {"uuid": str(ioc.segment_uuid), "offset": self.offset, "size": len(self.data), "did": did})


class InternalContentService:
    def __init__(self):
        self.loaded_content = {}
        self.outputs = {}

    def load(self, content_list: List[LoadedContent]):
        for c in content_list:
            self.loaded_content[c.segment.uuid] = c

    def put_str(self, did: str, string_data: str):
        segment = Segment(uuid=str(uuid.uuid4()), offset=0, size=len(string_data), did=did)
        self.outputs[segment.uuid] = string_data
        return segment

    def get_str(self, segments: List[Segment]):
        # TODO: String multiple segment ids together
        seg_id = segments[0].uuid
        return self.loaded_content[seg_id].data

    def get_bytes(self, segments: List[Segment]):
        seg_id = segments[0].uuid
        return self.loaded_content[seg_id].data.encode('utf-8')

    def get_output(self, seg_id: str):
        if seg_id in self.outputs:
            return self.outputs[seg_id]
        elif seg_id in self.loaded_content:
            return self.loaded_content[seg_id].data
        else:
            return None


class TestCaseBase(ABC):
    def __init__(self, data: Dict):
        """
        A test case for DeltaFi python actions
        :param data: Dict of test case fields
        - action: instance of the action being tested
        - data_dir: str: subdirectory name (e.g., test name) for locating test data files, i.e., test/data/{data_dir)
        - compare_tool: (optional) CompareHelper instanced for comparing output content
        - inputs: (optional) List[IOContent]: input content to action
        - parameters: (optional) Dict: map of action input parameters
        - in_meta: (optional) Dict: map of metadata as input to action
        - join_meta: (optional): List[Dict]: When a List is provided, this enables the JOIN portion of an action.
        When using JOIN, join_meta must match the size of inputs, though the Dict can be empty
        - did: (optional): str: overrides random DID
        """
        if "action" in data:
            self.action = data["action"]
        else:
            raise ValueError("action is required")

        if "data_dir" in data:
            self.data_dir = data["data_dir"]
        else:
            raise ValueError("data_dir is required")

        if "compare_tool" in data:
            self.compare_tool = data["compare_tool"]
        else:
            self.compare_tool = GenericCompareHelper()

        self.inputs = data["inputs"] if "inputs" in data else []
        self.file_name = data["file_name"] if "file_name" in data else "filename"
        self.parameters = data["parameters"] if "parameters" in data else {}
        self.in_meta = data["in_meta"] if "in_meta" in data else {}
        self.use_did = data["did"] if "did" in data else None
        self.expected_result_type = None
        self.err_or_filt_cause = None
        self.err_or_filt_context = None
        self.err_or_filt_annotations = None
        self.join_meta = data["join_meta"] if "join_meta" in data else None
        self.expected_metrics = []

    def add_metric(self, metric: Metric):
        self.expected_metrics.append(metric)

    def expect_error_result(self, cause: str, context: str, annotations: Dict = None):
        """
        A Sets the expected output of the action to an Error Result
        :param cause: the expected error cause
        :param context: the expected error context
        :param annotations: Dict: (Optional) the expected annotations
        """
        self.expected_result_type = ErrorResult
        self.err_or_filt_cause = cause
        self.err_or_filt_context = context
        self.err_or_filt_annotations = annotations

    def expect_filter_result(self, cause: str, context: str = None, annotations: Dict = None):
        """
        A Sets the expected output of the action to a Filter Result
        :param cause: the expected filter cause (message)
        :param context: (Optional) the expected filter context
        :param annotations: Dict: (Optional) the expected annotations
        """
        self.expected_result_type = FilterResult
        self.err_or_filt_cause = cause
        self.err_or_filt_context = context
        self.err_or_filt_annotations = annotations


class ActionTest(ABC):
    def __init__(self, package_name: str):
        """
        Provides structure for testing DeltaFi actions
        Args:
            package_name: name of the actions package for finding resources
        """
        self.content_service = InternalContentService()
        self.did = ""
        self.loaded_inputs = []
        self.package_name = package_name
        self.res_path = ""
        self.context = None

    def __reset__(self, did: str):
        self.content_service = InternalContentService()
        if did is None:
            self.did = str(uuid.uuid4())
        else:
            self.did = did
        self.loaded_inputs = []
        self.res_path = ""
        self.context = None

    def load_file(self, ioc: IOContent):
        file_res = self.res_path.joinpath(ioc.file_name)
        with file_res.open("r") as f:
            contents = f.read()
        return contents

    def get_contents(self, test_case: TestCaseBase):
        pkg_path = files(self.package_name)
        self.res_path = pkg_path.joinpath(f"test/data/{test_case.data_dir}/")

        # Load inputs
        for input_ioc in test_case.inputs:
            if not input_ioc.no_content and len(input_ioc.content_bytes) == 0:
                self.loaded_inputs.append(LoadedContent(self.did, input_ioc, self.load_file(input_ioc)))
            else:
                self.loaded_inputs.append(LoadedContent(self.did, input_ioc, None))

    def make_content_list(self):
        content_list = []
        for loaded_input in self.loaded_inputs:
            c = Content(name=loaded_input.name, segments=[loaded_input.segment], media_type=loaded_input.content_type,
                        content_service=self.content_service)
            content_list.append(c)
            loaded_input.content = c

        return content_list

    def make_df_msgs(self, test_case: TestCaseBase):
        content_list = self.make_content_list()
        self.content_service.load(self.loaded_inputs)

        delta_file_messages = []

        if test_case.join_meta is None:
            delta_file_messages.append(DeltaFileMessage(metadata=test_case.in_meta, content_list=content_list))
        else:
            for index, content in enumerate(content_list):
                delta_file_messages.append(DeltaFileMessage(
                    metadata=test_case.join_meta[index],
                    content_list=[content]))

        return delta_file_messages

    def make_context(self, test_case: TestCaseBase):
        action_name = INGRESS_FLOW + "." + test_case.action.__class__.__name__
        join = {} if test_case.join_meta else None
        self.context = Context(
            did=self.did,
            delta_file_name=test_case.file_name,
            data_source="DATASRC",
            flow_name=INGRESS_FLOW,
            flow_id="FLOWID",
            action_name=action_name,
            action_version="1.0",
            hostname=HOSTNAME,
            system_name=SYSTEM,
            content_service=self.content_service,
            saved_content=[],
            join=join,
            logger=get_logger())
        return self.context

    def make_event(self, test_case: TestCaseBase):
        return Event(delta_file_messages=self.make_df_msgs(test_case), context=self.make_context(test_case),
                     params=test_case.parameters, queue_name="", return_address="")

    def call_action(self, test_case: TestCaseBase):
        self.get_contents(test_case)
        return test_case.action.execute_action(self.make_event(test_case))

    def run_and_check_result_type(self, test_case: TestCaseBase, result_type):
        self.__reset__(test_case.use_did)
        result = self.call_action(test_case)

        if not isinstance(result, result_type):
            raise ValueError(f"Result type {result.__class__.__name__} does not match {result_type.__name__}")

        return result

    def execute_error(self, test_case: TestCaseBase):
        result = self.run_and_check_result_type(test_case, ErrorResult)
        resp = result.response()
        assert_equal_with_label(test_case.err_or_filt_cause, resp['cause'], "error cause")
        if test_case.err_or_filt_context is not None:
            assert_equal_with_label(test_case.err_or_filt_context, resp['context'], "error context")
        if test_case.err_or_filt_annotations is not None:
            assert_keys_and_values(test_case.err_or_filt_annotations, result.annotations)

    def execute_filter(self, test_case: TestCaseBase):
        result = self.run_and_check_result_type(test_case, FilterResult)
        resp = result.response()
        assert_equal_with_label(test_case.err_or_filt_cause, resp['message'], "filter cause")
        if test_case.err_or_filt_context is not None:
            assert_equal_with_label(test_case.err_or_filt_context, resp['context'], "filter context")
        if test_case.err_or_filt_annotations is not None:
            assert_keys_and_values(test_case.err_or_filt_annotations, result.annotations)

    def execute(self, test_case: TestCaseBase):
        if test_case.expected_result_type == ErrorResult:
            self.execute_error(test_case)
        elif test_case.expected_result_type == FilterResult:
            self.execute_filter(test_case)
        else:
            raise ValueError(f"unknown type: {test_case.expected_result_type}")

    @staticmethod
    def compare_content_details(expected: LoadedContent, actual: Content):
        assert_equal(expected.content_type, actual.media_type)
        assert_equal(expected.name, actual.name)

    def compare_one_content(self, comparator: CompareHelper, expected: LoadedContent, actual, index):
        self.compare_content_details(expected, actual)
        seg_id = actual.segments[0].uuid
        comparator.compare(expected.data, self.content_service.get_output(seg_id), f"Content[{index}]")

    def compare_content_list(self, comparator: CompareHelper, expected_outputs: List[IOContent], content: List):
        assert_equal_len(expected_outputs, content)
        for index, expected_ioc in enumerate(expected_outputs):
            if not expected_ioc.no_content and len(expected_ioc.content_bytes) == 0:
                expected = LoadedContent(self.did, expected_ioc, self.load_file(expected_ioc))
            else:
                expected = LoadedContent(self.did, expected_ioc, None)
            self.compare_one_content(comparator, expected, content[index], index)

    @staticmethod
    def compare_one_metric(expected: Metric, result: Metric):
        assert expected.name == result.name
        assert_equal_with_label(expected.value, result.value, expected.name)
        assert_keys_and_values(expected.tags, result.tags)

    def compare_metrics(self, expected_metrics: List[Metric], results: List[Metric]):
        if len(expected_metrics) > 0:
            assert_equal_len(expected_metrics, results)
            for index, expected in enumerate(expected_metrics):
                self.compare_one_metric(expected, results[index])

    def has_saved_content__size(self, count: int):
        assert_equal_with_label(
            count, len(self.context.saved_content), "savedContent")
