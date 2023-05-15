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

import pytest
from deltafi.action import LoadAction
from deltafi.domain import Context, Event
from deltafi.input import LoadInput
from deltafi.result import LoadResult, EnrichResult
from deltafi.storage import ContentService
from mockito import mock, unstub
from pydantic import BaseModel, Field

from .helperutils import *


class SampleLoadParameters(BaseModel):
    domain: str = Field(description="The domain used by the load action")


class SampleLoadAction(LoadAction):
    def __init__(self):
        super().__init__('A sample load action')

    def param_class(self):
        return SampleLoadParameters

    def load(self, context: Context, params: SampleLoadParameters, load_input: LoadInput):
        return LoadResult(context).add_metadata('loadKey', 'loadValue') \
            .add_domain(params.domain, 'the domain value!', 'text/plain')


class InvalidResult(LoadAction):
    def __init__(self):
        super().__init__('A sample load action')

    def load(self, context: Context, params: SampleLoadParameters, load_input: LoadInput):
        return EnrichResult(context)


def make_event(content_service):
    logger = None
    event = Event.create({
        'deltaFileMessages': [make_delta_file_message_dict()],
        'actionContext': make_context_dict(),
        'actionParams': {
            "domain": "theDomainName"
        }
    },
        "HOSTNAME", content_service, logger)
    return event


def test_load_action():
    unstub()
    mock_content_service = mock(ContentService)

    action = SampleLoadAction()
    assert action.action_type.value == "load"
    result = action.execute(make_event(mock_content_service))
    assert type(result) == LoadResult

    expected_response = {
        'domains': [
            {
                'mediaType': 'text/plain',
                'name': 'theDomainName',
                'value': 'the domain value!'
            }
        ],
        'content': [],
        'metadata': {
            'loadKey': 'loadValue'
        }
    }
    assert result.response() == expected_response


def test_invalid_result():
    unstub()
    mock_content_service = mock(ContentService)

    action = InvalidResult()
    with pytest.raises(ValueError):
        result = action.execute(make_event(mock_content_service))
