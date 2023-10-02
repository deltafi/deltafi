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
from deltafi.action import DomainAction, LoadAction
from deltafi.domain import Context, Content
from deltafi.input import DomainInput, LoadInput
from deltafi.result import DomainResult, LoadResult
from deltafi.storage import ContentService
from mockito import when, mock, unstub, verifyStubbedInvocationsAreUsed
from pydantic import BaseModel, Field


class SampleDomainAction(DomainAction):
    def __init__(self):
        super().__init__('Domain action description', ['domain1', 'domain2'])

    def domain(self, context: Context, params: BaseModel, domain_input: DomainInput):
        return DomainResult(context).annotate('theIndexMetaKey', 'theIndexMetaValue')


class SampleLoadParameters(BaseModel):
    domain: str = Field(description="The domain used by the load action")


class SampleLoadAction(LoadAction):
    def __init__(self):
        super().__init__('Domain action description')

    def param_class(self):
        return SampleLoadParameters

    def load(self, context: Context, params: SampleLoadParameters, load_input: LoadInput):
        return LoadResult(context).add_metadata('loadKey', 'loadValue') \
            .add_domain(params.domain, 'Python domain!', 'text/plain') \
            .add_content(Content(name='loaded content', segments=[], media_type='text/plain',
                                 content_service=mock(ContentService)))


class SampleAbstractLoadAction(LoadAction):
    def __init__(self):
        super().__init__('Domain action description - ignored due to the abstract method')

    def param_class(self):
        return SampleLoadParameters

    @abstractmethod
    def extra_abstract_method(self):
        pass

    def load(self, context: Context, params: SampleLoadParameters, load_input: LoadInput):
        return LoadResult(context).add_metadata('loadKey', 'loadValue') \
            .add_domain(params.domain, 'Python domain!', 'text/plain') \
            .add_content(Content(name='loaded content', segments=[], media_type='text/plain',
                                 content_service=mock(ContentService)))