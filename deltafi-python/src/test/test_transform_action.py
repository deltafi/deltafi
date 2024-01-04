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
from deltafi.action import TransformAction
from deltafi.actiontype import ActionType
from deltafi.domain import Event
from deltafi.input import TransformInput
from deltafi.result import TransformResult, EgressResult
from deltafi.storage import ContentService
from mockito import mock, unstub
from pydantic import BaseModel, Field

from .helperutils import *


class SampleTransformParameters(BaseModel):
    thing: str = Field(description="An action parameter")


class SampleTransformAction(TransformAction):
    def __init__(self):
        super().__init__('A sample transform action')

    def param_class(self):
        return SampleTransformParameters

    def transform(self, context: Context, params: SampleTransformParameters, transform_input: TransformInput):
        return TransformResult(context).add_metadata('transformKey', 'transformValue') \
            .annotate('transformAnnotate', 'transformAnnotateValue')


class InvalidResult(TransformAction):
    def __init__(self):
        super().__init__('A sample transform action')

    def transform(self, context: Context, params: SampleTransformParameters, transform_input: TransformInput):
        return EgressResult(context, 'destination', 42)


def make_event(content_service):
    logger = None
    event = Event.create({
        'deltaFileMessages': [make_delta_file_message_dict()],
        'actionContext': make_context_dict(),
        'actionParams': {
            "thing": "theThing"
        }
    },
        "HOSTNAME", content_service, logger)
    return event


def test_transform_action():
    unstub()
    mock_content_service = mock(ContentService)

    action = SampleTransformAction()
    assert action.action_type.value == ActionType.TRANSFORM.value
    result = action.execute_action(make_event(mock_content_service))
    assert type(result) == TransformResult

    expected_response = {
        'content': [],
        'metadata': {
            'transformKey': 'transformValue'
        },
        'annotations': {
            'transformAnnotate': 'transformAnnotateValue'
        },
        'deleteMetadataKeys': []
    }
    assert result.response() == expected_response


def test_invalid_result():
    unstub()
    mock_content_service = mock(ContentService)

    action = InvalidResult()
    with pytest.raises(ValueError):
        action.execute_action(make_event(mock_content_service))
