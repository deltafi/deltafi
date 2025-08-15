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

from unittest.mock import patch

import pytest
from deltafi.action import TransformAction
from deltafi.actiontype import ActionType
from deltafi.input import TransformInput
from deltafi.logger import get_logger
from deltafi.plugin import Plugin
from deltafi.result import TransformResult, EgressResult, ErrorResult
from deltafi.storage import ContentService
from mockito import when, mock, unstub
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
        # Creates two orphaned contents:
        TransformResult(context).save_string_content("123", "temp1.txt", "text/plain")
        TransformResult(context).save_string_content("abcdefg", "temp2.txt", "text/plain")

        return TransformResult(context) \
            .add_metadata('transformKey', 'transformValue') \
            .annotate('transformAnnotate', 'transformAnnotateValue') \
            .save_string_content("Abcde12345", "ten.txt", "text.plain", {"tag"})


class InvalidResult(TransformAction):
    def __init__(self):
        super().__init__('A sample transform action')

    def transform(self, context: Context, params: SampleTransformParameters, transform_input: TransformInput):
        return EgressResult(context)


class SampleErrorAction(TransformAction):
    def __init__(self):
        super().__init__('Create content but return error')

    def transform(self, context: Context, params: SampleTransformParameters, transform_input: TransformInput):
        # Creates orphaned content:
        TransformResult(context).save_string_content("123", "temp1.txt", "text/plain")

        return ErrorResult(context, 'Something bad happened', 'details')


@patch('time.time')
def test_action_returns_error(mock_time):
    unstub()
    mock_content_service = mock(ContentService)
    mock_time.return_value = 1754999744

    when(mock_content_service).put_str(...).thenReturn(make_segment('000'))
    when(mock_content_service).delete_all(...).thenReturn([])

    action = SampleErrorAction()
    event = make_event(mock_content_service)
    result = action.execute_action(event)
    assert type(result) == ErrorResult

    expected_response = {
        'annotations': {},
        'cause': 'Something bad happened',
        'context': 'details'
    }
    assert result.response() == expected_response

    plugin_to_response = Plugin.to_response(event, '12:00', '12:01', result)

    expected_plugin_to_response = {
        'actionName': 'ACTION_NAME',
        'did': '123did',
        'error': {
            'annotations': {},
            'cause': 'Something bad happened',
            'context': 'details'
        },
        'flowId': 'FLOW_ID',
        'flowName': 'FLOW_NAME',
        'messages': [
            {
                'severity': 'ERROR',
                'created': 1754999744,
                'source': 'ACTION_NAME',
                'message': "Something bad happened\ndetails"
            }
        ],
        'metrics': [],
        'start': '12:00',
        'stop': '12:01',
        'type': 'ERROR'
    }

    assert plugin_to_response == expected_plugin_to_response

    orphaned_content = Plugin.find_unused_content(event.context.saved_content, result)
    assert (len(orphaned_content)) == 1
    assert orphaned_content[0].uuid == "000"

    Plugin.orphaned_content_check(get_logger(), event.context, result, plugin_to_response)


def test_action_returns_transform():
    unstub()
    mock_content_service = mock(ContentService)

    when(mock_content_service).put_str(...) \
        .thenReturn(make_segment('111')) \
        .thenReturn(make_segment('222')) \
        .thenReturn(make_segment('333'))

    action = SampleTransformAction()
    assert action.action_type.value == ActionType.TRANSFORM.value
    event = make_event(mock_content_service)
    result = action.execute_action(event)
    assert type(result) == TransformResult

    expected_response = [{
        'did': '123did',
        'content': [
            {
                'mediaType': 'text.plain',
                'name': 'ten.txt',
                'segments': [
                    {
                        'did': '123did',
                        'offset': 0,
                        'size': 100,
                        'uuid': '333'
                    }
                ],
                'tags': ['tag']
            }
        ],
        'metadata': {
            'transformKey': 'transformValue'
        },
        'annotations': {
            'transformAnnotate': 'transformAnnotateValue'
        },
        'deleteMetadataKeys': []
    }]
    assert result.response() == expected_response

    orphaned_content = Plugin.find_unused_content(event.context.saved_content, result)
    assert (len(orphaned_content)) == 2
    assert orphaned_content[0].uuid == "111"
    assert orphaned_content[1].uuid == "222"


def test_invalid_result():
    unstub()
    mock_content_service = mock(ContentService)

    action = InvalidResult()
    with pytest.raises(ValueError):
        action.execute_action(make_event(mock_content_service))
