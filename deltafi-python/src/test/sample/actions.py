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

from abc import abstractmethod
from typing import Dict, List

from deltafi.action import ActionOptions, DescriptionWithConditions, InputSpec, OutputSpec, TransformAction
from deltafi.domain import Context, Content
from deltafi.input import TransformInput
from deltafi.result import TransformResult
from deltafi.storage import ContentService
from mockito import mock
from pydantic import BaseModel, Field


class SampleTransformParameters(BaseModel):
    a_string: str = Field(description="this string parameter is required")
    def_string: str = Field(default="default-val", description="str with default")
    a_dict: dict[str, str] = Field(description="this dict parameter is required")
    def_dict: Dict[str, str] = Field(default={"key1:" "val1"}, description="dict has default")
    a_list: List[str] = Field(default=[], description="list with default")
    a_bool: bool = Field(description="this boolean parameter is required")
    def_int: int = Field(default=100, description="int with default")


class SampleTransformAction(TransformAction):
    def __init__(self):
        super().__init__('', ActionOptions(description='Transform action description',
                                           input_spec=InputSpec(content_summary='The input content summary', metadata_summary='The input metadata summary'),
                                           output_spec=OutputSpec(content_summary='The output content summary', metadata_summary='The output metadata summary', annotations_summary='The output annotations summary'),
                                           filters=[DescriptionWithConditions('Filter 1', ['Condition A', 'Condition B']),
                                                   'Filter 2'],
                                           errors=[DescriptionWithConditions('Error 1', ['Condition A', 'Condition B']),
                                                   'Error 2'],
                                           notes=['Note 1', 'Note2'],
                                           details='The details'))

    def param_class(self):
        return SampleTransformParameters

    def transform(self, context: Context, params: SampleTransformParameters, transform_input: TransformInput):
        return TransformResult(context).add_metadata('transformKey', 'transformValue') \
            .add_content(Content(name='transformed content', segments=[], media_type='text/plain',
                                 content_service=mock(ContentService)))


class SampleAbstractTransformAction(TransformAction):
    def __init__(self):
        super().__init__('Transform action description - ignored due to the abstract method')

    def param_class(self):
        return SampleTransformParameters

    @abstractmethod
    def extra_abstract_method(self):
        pass

    def transform(self, context: Context, params: SampleTransformParameters, transform_input: TransformInput):
        return TransformResult(context).add_metadata('transformKey', 'transformValue') \
            .add_content(Content(name='transformed content', segments=[], media_type='text/plain',
                                 content_service=mock(ContentService)))
