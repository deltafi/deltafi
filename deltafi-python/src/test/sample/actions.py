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

from abc import abstractmethod
from deltafi.action import TransformAction
from deltafi.domain import Context, Content
from deltafi.input import TransformInput
from deltafi.result import TransformResult
from deltafi.storage import ContentService
from mockito import when, mock, unstub, verifyStubbedInvocationsAreUsed
from pydantic import BaseModel, Field


class SampleTransformParameters(BaseModel):
    thing: str = Field(description="Sample transform parameter")


class SampleTransformAction(TransformAction):
    def __init__(self):
        super().__init__('Transform action description')

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
