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

import uuid
from abc import ABC
from importlib.resources import files
from typing import List

from deltafi.domain import DeltaFileMessage, Event, Content, Context
from deltafi.logger import get_logger
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
        file_name (str): The name of file in test/data.
        content_name (str): The name of the content.
        content_type (str): The media type of the content
        offset (int): Offset to use in Segment
        content_bytes (str): Bypass file read, and uses these bytes for content
    """

    def __init__(self, file_name: str, content_name: str = None,
                 content_type: str = None, offset: int = 0,
                 content_bytes: str = ""):
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
        self.content_bytes = content_bytes

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
            self.data = ioc.content_bytes
        self.segment = Segment.from_dict({
            "uuid": str(uuid.uuid4()),
            "offset": self.offset,
            "size": len(self.data),
            "did": did
        })


class InternalContentService:
    def __init__(self):
        self.loaded_content = {}
        self.outputs = {}

    def load(self, content_list: List[LoadedContent]):
        for c in content_list:
            self.loaded_content[c.segment.uuid] = c

    def put_str(self, did: str, string_data: str):
        segment = Segment(uuid=str(uuid.uuid4()),
                          offset=0,
                          size=len(string_data),
                          did=did)
        self.outputs[segment.uuid] = string_data
        return segment

    def get_str(self, segments: List[Segment]):
        # TODO: String multiple segment ids together
        seg_id = segments[0].uuid
        return self.loaded_content[seg_id].data

    def get_output(self, seg_id: str):
        return self.outputs[seg_id]


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
        - in_domains: (optional) List[Domain]: list of domains as input to action
        - in_enrichments: (optional) List[Domain]: list of enrichments as input to action
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
        self.outputs = data["outputs"] if "outputs" in data else []
        self.parameters = data["parameters"] if "parameters" in data else {}
        self.in_meta = data["in_meta"] if "in_meta" in data else {}
        self.in_domains = data["in_domains"] if "in_domains" in data else []
        self.in_enrichments = data["in_enrichments"] if "in_enrichments" in data else []
        self.expected_result_type = None
        self.cause_regex = None
        self.context_regex = None

    def expect_error_result(self, cause: str, context: str):
        """
        A Sets the expected output of the action to an Error Result
        :param cause: the expected error cause
        :param context: the expected error context
        """
        self.expected_result_type = ErrorResult
        self.cause_regex = cause
        self.context_regex = context

    def expect_filter_result(self, cause: str):
        """
        A Sets the expected output of the action to a Filter Result
        :param cause: the expected filter cause (message)
        """
        self.expected_result_type = FilterResult
        self.cause_regex = cause


class ActionTest(ABC):
    def __init__(self, package_name: str):
        """
        Provides structure for testing DeltaFi actions
        Args:
            package_name: name of the actions package for finding resources
        """
        self.content_service = InternalContentService()
        self.did = ""
        self.expected_outputs = []
        self.loaded_inputs = []
        self.package_name = package_name
        self.res_path = ""

    def __reset__(self):
        self.content_service = InternalContentService()
        self.did = str(uuid.uuid4())
        self.expected_outputs = []
        self.loaded_inputs = []
        self.res_path = ""

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
            if len(input_ioc.content_bytes) == 0:
                self.loaded_inputs.append(LoadedContent(self.did, input_ioc, self.load_file(input_ioc)))
            else:
                self.loaded_inputs.append(LoadedContent(self.did, input_ioc, None))

        # Load expected outputs
        for output_ioc in test_case.outputs:
            if len(output_ioc.content_bytes) == 0:
                self.expected_outputs.append(LoadedContent(self.did, output_ioc, self.load_file(output_ioc)))
            else:
                self.expected_outputs.append(LoadedContent(self.did, output_ioc, None))

    def make_content_list(self, test_case: TestCaseBase):
        content_list = []
        for loaded_input in self.loaded_inputs:
            c = Content(name=loaded_input.name,
                        segments=[loaded_input.segment],
                        media_type=loaded_input.content_type,
                        content_service=self.content_service)
            content_list.append(c)
            loaded_input.content = c

        return content_list

    def make_df_msg(self, test_case: TestCaseBase):
        content_list = self.make_content_list(test_case)
        self.content_service.load(self.loaded_inputs)

        return DeltaFileMessage(metadata=test_case.in_meta,
                                content_list=content_list,
                                domains=test_case.in_domains,
                                enrichments=test_case.in_enrichments)

    def make_context(self, test_case: TestCaseBase):
        action_name = INGRESS_FLOW + "." + test_case.action.__class__.__name__
        return Context(did=self.did,
                       action_flow=INGRESS_FLOW,
                       action_name=action_name,
                       source_filename=test_case.file_name,
                       ingress_flow=INGRESS_FLOW,
                       egress_flow=EGRESS_FLOW,
                       system=SYSTEM,
                       hostname=HOSTNAME,
                       content_service=self.content_service,
                       logger=get_logger())

    def make_event(self, test_case: TestCaseBase):
        return Event(
            delta_file_messages=[self.make_df_msg(test_case)],
            context=self.make_context(test_case),
            params=test_case.parameters,
            queue_name="",
            return_address="")

    def call_action(self, test_case: TestCaseBase):
        self.get_contents(test_case)
        return test_case.action.execute(self.make_event(test_case))

    def run_and_check_result_type(self, test_case: TestCaseBase, result_type):
        self.__reset__()
        result = self.call_action(test_case)

        if not isinstance(result, result_type):
            raise ValueError(f"Result type {result.__class__.__name__} does not match {result_type.__name__}")

        return result

    def execute_error(self, test_case: TestCaseBase):
        result = self.run_and_check_result_type(test_case, ErrorResult)
        resp = result.response()
        assert resp['cause'] == test_case.cause_regex
        assert resp['context'] == test_case.context_regex

    def execute_filter(self, test_case):
        result = self.run_and_check_result_type(test_case, FilterResult)
        resp = result.response()
        assert resp['message'] == test_case.cause_regex

    def execute(self, test_case: TestCaseBase):
        if isinstance(test_case.expected_result_type, ErrorResult.__class__):
            self.execute_error(test_case)
        elif isinstance(test_case.expected_result_type, FilterResult.__class__):
            self.execute_filter(test_case)
        else:
            raise ValueError(f"unknown type: {test_case.expected_result_type}")

    def compare_content_details(self, expected: LoadedContent, actual: Content):
        assert_equal(expected.content_type, actual.media_type)
        assert_equal(expected.name, actual.name)

    def compare_one_content(self, comparitor: CompareHelper, expected: LoadedContent, actual, index):
        self.compare_content_details(expected, actual)
        seg_id = actual.segments[0].uuid
        comparitor.compare(
            expected.data,
            self.content_service.get_output(seg_id),
            f"Content[{index}]"
        )

    def compare_all_output(self, comparitor: CompareHelper, content: List):
        assert_equal_len(self.expected_outputs, content)
        for index, expected in enumerate(self.expected_outputs):
            self.compare_one_content(comparitor, expected, content[index], index)

    def compare_domains(self, comparitor: CompareHelper, expected_items: List[Dict], results: List[Dict]):
        assert_equal_len(expected_items, results)
        for index, expected in enumerate(expected_items):
            actual = results[index]
            assert_equal(expected['name'], actual['name'])
            assert_equal(expected['mediaType'], actual['mediaType'])

            expected_value = expected['value']
            if type(expected_value) == str:
                comparitor.compare(expected_value, actual['value'], f"Domain[{index}]")
            elif type(expected_value) == IOContent:
                expected_data = self.load_file(expected_value)
                comparitor.compare(expected_data, actual['value'], f"Domain[{index}]")
            else:
                raise ValueError(
                    f"unknown expected_value type: {type(expected_value)}")
