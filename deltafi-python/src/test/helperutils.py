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

from deltafi.domain import Context
from deltafi.storage import Segment

SEG_ID = "1"
TEST_DID = "123"


def make_segment(seg_id):
    return Segment(uuid=seg_id, offset=0, size=100, did=TEST_DID)


def make_context_dict():
    return {
        'did': TEST_DID,
        'sourceFilename': 'FILENAME',
        'flow': "ACTION_FLOW",
        'name': "ACTION_FLOW.ACTION_NAME_IN_FLOW",
        'ingressFlow': "IN",
        'egressFlow': "OUT",
        'systemName': "SYSTEM",
        'collect': None,
        'collectedDids': None,
        'memo': 'note to self'
    }


def make_content_dict(name):
    return {
        'name': name,
        'segments': [make_segment(SEG_ID).json()],
        'mediaType': 'xml'
    }


def make_delta_file_message_dict():
    return {
        'ingressFlow': "FLOW",
        'contentList': [make_content_dict("CONTENT_NAME")],
        'metadata': {'plKey1': 'valueA', 'plKey2': 'valueB'},
        'domains': [
            {'name': "DOMAIN1", 'value': "VALUE1", 'mediaType': "MEDIA_TYPE1"},
            {'name': "DOMAIN2", 'value': "VALUE2", 'mediaType': "MEDIA_TYPE2"}
        ],
        'enrichments': [
            {'name': "ENRICH1", 'value': "VALUE1", 'mediaType': "MEDIA_TYPE1"},
            {'name': "ENRICH2", 'value': "VALUE2", 'mediaType': "MEDIA_TYPE2"},
            {'name': "ENRICH3", 'value': "VALUE3", 'mediaType': "MEDIA_TYPE3"}
        ]
    }


def make_context():
    return Context(did="did",
                   action_flow="action_flow",
                   action_name="action_name",
                   source_filename="source_filename",
                   ingress_flow="ingress_flow",
                   egress_flow="egress_flow",
                   system="system",
                   hostname="hostname",
                   content_service=None)