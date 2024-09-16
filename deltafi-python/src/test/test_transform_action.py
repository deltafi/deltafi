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

import pytest
from mockito import when, mock, unstub
from pydantic import BaseModel, Field

from deltafi.action import TransformAction
from deltafi.actiontype import ActionType
from deltafi.input import TransformInput
from deltafi.plugin import Plugin
from deltafi.result import TransformResult, EgressResult, ErrorResult
from deltafi.storage import ContentService
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


class SampleErrorAction(TransformAction):
    def __init__(self):
        super().__init__('Create content but return error')

    def transform(self, context: Context, params: SampleTransformParameters, transform_input: TransformInput):
        result = (TransformResult(context)
                  .save_string_content('saved_content', 'saved_content', 'text/plan'))

        return ErrorResult(context, 'Something bad happened', '')


def test_action_returns_error():
    unstub()
    mock_content_service = mock(ContentService)

    when(mock_content_service).put_str(...).thenReturn(make_segment('222'))

    action = SampleErrorAction()
    event = make_event(mock_content_service)
    result = action.execute_action(event)
    assert type(result) == ErrorResult

    expected_response = {
        'annotations': {},
        'cause': 'Something bad happened',
        'context': ''
    }
    assert result.response() == expected_response

    plugin_to_response = Plugin.to_response(event, '12:00', '12:01', result)
    assert len(plugin_to_response['savedContent']) == 1

    expected_plugin_to_response = {
        'actionId': 'ACTION_ID',
        'actionName': 'ACTION_NAME',
        'did': '123',
        'error': {
            'annotations': {},
            'cause': 'Something bad happened',
            'context': ''
        },
        'flowId': 'FLOW_ID',
        'flowName': 'FLOW_NAME',
        'metrics': [],
        'savedContent': [
            {
                'mediaType': 'text/plan',
                'name': 'saved_content',
                'segments': [{'did': '123',
                              'offset': 0,
                              'size': 100,
                              'uuid': '222'}]
            }
        ],
        'start': '12:00',
        'stop': '12:01',
        'type': 'ERROR'
    }

    assert plugin_to_response == expected_plugin_to_response


def test_action_returns_transform():
    unstub()
    mock_content_service = mock(ContentService)

    action = SampleTransformAction()
    assert action.action_type.value == ActionType.TRANSFORM.value
    event = make_event(mock_content_service)
    result = action.execute_action(event)
    assert type(result) == TransformResult

    expected_response = [{
        'content': [],
        'metadata': {
            'transformKey': 'transformValue'
        },
        'annotations': {
            'transformAnnotate': 'transformAnnotateValue'
        },
        'deleteMetadataKeys': []
    }]
    assert result.response() == expected_response

    plugin_to_response = Plugin.to_response(event, '12:00', '12:01', result)
    assert len(plugin_to_response['savedContent']) == 0


def test_invalid_result():
    unstub()
    mock_content_service = mock(ContentService)

    action = InvalidResult()
    with pytest.raises(ValueError):
        action.execute_action(make_event(mock_content_service))
