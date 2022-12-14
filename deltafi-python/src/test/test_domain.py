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
from deltafi.domain import Content, Context, DeltaFile, Domain, Event, FormattedData, ProtocolLayer, SourceInfo
from deltafi.storage import ContentService
from mockito import mock, unstub

from .helperutils import make_content_reference, TEST_DID

SEG_ID = "1"


def make_key_val_list():
    return [
        {'key': 'key1', 'value': 'value1'},
        {'key': 'key2', 'value': 'value2'}]


def check_meta_list(meta):
    assert meta[0]["key"] == "key1"
    assert meta[0]["value"] == "value1"
    assert meta[1]["key"] == "key2"
    assert meta[1]["value"] == "value2"


def check_meta_dict(meta):
    assert meta["key1"] == "value1"
    assert meta["key2"] == "value2"


def make_content_dict(name):
    return {
        'name': name,
        'metadata': make_key_val_list(),
        'contentReference': make_content_reference(SEG_ID).json()}


def make_content(name):
    return Content.from_dict(make_content_dict(name))


def test_content_json():
    content = make_content("CONTENT_NAME")
    content_json = content.json()
    assert content_json["name"] == "CONTENT_NAME"
    check_meta_list(content_json["metadata"])


def make_context_dict():
    return {
        'did': TEST_DID,
        'ingressFlow': "IN",
        'name': "ACTION_NAME",
        'egressFlow': "OUT",
        'systemName': "SYSTEM"
    }


def test_context_json():
    unstub()
    mock_content_service = mock(ContentService)
    logger = None
    context = Context.create(
        make_context_dict(),
        "HOSTNAME", mock_content_service, logger)

    assert context.did == TEST_DID
    assert context.action_name == "ACTION_NAME"
    assert context.egress_flow == "OUT"
    assert context.system == "SYSTEM"
    assert context.hostname == "HOSTNAME"
    assert context.ingress_flow == "IN"
    assert context.content_service == mock_content_service
    assert context.logger is None


def test_domain():
    domain = Domain.from_dict({
        'name': "NAME",
        'value': "VALUE",
        'mediaType': "MEDIA_TYPE"})
    assert domain.name == "NAME"
    assert domain.value == "VALUE"
    assert domain.media_type == "MEDIA_TYPE"


def test_formatted_data():
    formatted_data = FormattedData.from_dict({
        'filename': "FILENAME",
        'formatAction': "FORMAT_ACTION",
        'metadata': make_key_val_list(),
        'contentReference': make_content_reference(SEG_ID).json()})

    assert formatted_data.filename == "FILENAME"
    assert formatted_data.format_action == "FORMAT_ACTION"
    check_meta_dict(formatted_data.metadata)


def make_protocol_layer_dict():
    return {
        'action': "ACTION",
        'content': [make_content_dict("CONTENT_NAME")],
        'metadata': make_key_val_list()}


def test_protocol_layer():
    protocol_layer = ProtocolLayer.from_dict(make_protocol_layer_dict())
    assert protocol_layer.action == "ACTION"
    assert protocol_layer.content[0].name == "CONTENT_NAME"
    check_meta_dict(protocol_layer.metadata)


def make_source_info_dict():
    return {
        'filename': "FILENAME",
        'flow': "FLOW",
        'metadata': make_key_val_list()}


def test_source_info():
    source_info = SourceInfo.from_dict(make_source_info_dict())
    assert source_info.filename == "FILENAME"
    assert source_info.flow == "FLOW"
    check_meta_dict(source_info.metadata)


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


def test_delta_file():
    delta_file = DeltaFile.from_dict(make_delta_file_dict())
    assert delta_file.did == TEST_DID
    assert delta_file.formatted_data.filename == "FN1"
    assert len(delta_file.domains) == 2
    assert len(delta_file.enrichment) == 3
    assert delta_file.formatted_data.content_reference.segments[0].uuid == SEG_ID


def test_event():
    unstub()
    mock_content_service = mock(ContentService)
    logger = None
    event = Event.create({
        'deltaFile': make_delta_file_dict(),
        'actionContext': make_context_dict(),
        'actionParams': {}
    },
        "HOSTNAME", mock_content_service, logger)

    assert event.delta_file.did == TEST_DID
    assert event.context.did == TEST_DID
    assert event.context.ingress_flow == "IN"
    assert event.context.content_service == mock_content_service
    assert event.context.logger is None
    assert len(event.params) == 0
