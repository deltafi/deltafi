"""
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
"""
from deltafi.storage import ContentReference, Segment

SEG_ID = "1"
TEST_DID = "123"


def make_segment(seg_id):
    segment = Segment(uuid=seg_id, offset=0, size=100, did=TEST_DID)
    return segment


def make_content_reference(seg_id):
    segment = make_segment(seg_id)
    content_reference = ContentReference(segments=[segment], media_type="xml")
    return content_reference


def make_context_dict():
    return {
        'did': TEST_DID,
        'ingressFlow': "IN",
        'name': "ACTION_NAME_IN_FLOW",
        'egressFlow': "OUT",
        'systemName': "SYSTEM"
    }


def make_key_val_list():
    return [
        {'key': 'key1', 'value': 'value1'},
        {'key': 'key2', 'value': 'value2'}]


def make_pl_key_val_list():
    return [
        {'key': 'plKey1', 'value': 'valueA'},
        {'key': 'plKey2', 'value': 'valueB'}]


def make_source_info_dict():
    return {
        'filename': "FILENAME",
        'flow': "FLOW",
        'metadata': make_key_val_list()}


def make_content_dict(name):
    return {
        'name': name,
        'metadata': make_key_val_list(),
        'contentReference': make_content_reference(SEG_ID).json()}


def make_protocol_layer_dict():
    return {
        'action': "ACTION",
        'content': [make_content_dict("CONTENT_NAME")],
        'metadata': make_pl_key_val_list()}


def make_delta_file_dict():
    return {
        'did': TEST_DID,
        'sourceInfo': make_source_info_dict(),
        'protocolStack': [make_protocol_layer_dict()],
        'domains': [
            {'name': "DOMAIN1", 'value': "VALUE1", 'mediaType': "MEDIA_TYPE1"},
            {'name': "DOMAIN2", 'value': "VALUE2", 'mediaType': "MEDIA_TYPE2"}
        ],
        'indexedMetadata': {},
        'enrichment': [
            {'name': "ENRICH1", 'value': "VALUE1", 'mediaType': "MEDIA_TYPE1"},
            {'name': "ENRICH2", 'value': "VALUE2", 'mediaType': "MEDIA_TYPE2"},
            {'name': "ENRICH3", 'value': "VALUE3", 'mediaType': "MEDIA_TYPE3"}
        ],
        'formattedData': [
            {
                'filename': "FN1",
                'formatAction': "FORMAT_ACTION",
                'metadata': [],
                'contentReference': make_content_reference(SEG_ID).json()
            },
            {
                'filename': "FN2",
                'formatAction': "FORMAT_ACTION2",
                'metadata': [],
                'contentReference': make_content_reference("2").json()
            }
        ]
    }
