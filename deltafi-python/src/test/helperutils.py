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

from deltafi.domain import Context, Event
from deltafi.storage import Segment

SEG_ID = "1"
TEST_DID = "123did"


def make_segment(seg_id):
    return Segment(uuid=seg_id, offset=0, size=100, did=TEST_DID)


def make_context_dict():
    return {
        'did': TEST_DID,
        'deltaFileName': 'FILENAME',
        'dataSource': 'DATA_SOURCE',
        'flowName': 'FLOW_NAME',
        'flowId': 'FLOW_ID',
        'actionName': 'ACTION_NAME',
        'actionVersion': '1.0',
        'hostname': 'HOSTNAME',
        'systemName': 'SYSTEM_NAME'
    }


def make_content_dict(name):
    return {
        'name': name,
        'segments': [make_segment(SEG_ID).json()],
        'mediaType': 'xml'
    }


def make_delta_file_message_dict():
    return {
        'metadata': {'plKey1': 'valueA', 'plKey2': 'valueB'},
        'contentList': [make_content_dict('CONTENT_NAME'), make_content_dict('CONTENT 2')]
    }


def make_context():
    return Context(did=TEST_DID,
                   delta_file_name='FILENAME',
                   data_source='DATA_SOURCE',
                   flow_name='FLOW_NAME',
                   flow_id='FLOW_ID',
                   action_name='ACTION_NAME',
                   action_version='1.0',
                   hostname='HOSTNAME',
                   system_name='SYSTEM_NAME',
                   content_service=None)


def make_event(content_service):
    logger = None
    event = Event.create({
        'deltaFileMessages': [make_delta_file_message_dict()],
        'actionContext': make_context_dict(),
        'actionParams': {
            "thing": "theThing"
        }
    }, content_service, logger)
    return event
