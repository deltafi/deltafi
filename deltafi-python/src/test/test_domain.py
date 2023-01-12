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

from .helperutils import *


def check_meta(meta):
    assert meta["key1"] == "value1"
    assert meta["key2"] == "value2"


def check_pl_meta(meta):
    assert meta["plKey1"] == "valueA"
    assert meta["plKey2"] == "valueB"


def make_content(name):
    return Content.from_dict(make_content_dict(name))


def test_content_json():
    content = make_content("CONTENT_NAME")
    content_json = content.json()
    assert content_json["name"] == "CONTENT_NAME"
    assert content_json["metadata"]["key1"] == "value1"
    assert content_json["metadata"]["key2"] == "value2"


def test_context_json():
    unstub()
    mock_content_service = mock(ContentService)
    logger = None
    context = Context.create(
        make_context_dict(),
        "HOSTNAME", mock_content_service, logger)

    assert context.did == TEST_DID
    assert context.action_name == "ACTION_NAME_IN_FLOW"
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
        'metadata': {'key1': 'value1', 'key2': 'value2'},
        'contentReference': make_content_reference(SEG_ID).json()})

    assert formatted_data.filename == "FILENAME"
    assert formatted_data.format_action == "FORMAT_ACTION"
    check_meta(formatted_data.metadata)


def test_protocol_layer():
    protocol_layer = ProtocolLayer.from_dict(make_protocol_layer_dict())
    assert protocol_layer.action == "ACTION"
    assert protocol_layer.content[0].name == "CONTENT_NAME"
    check_pl_meta(protocol_layer.metadata)


def test_source_info():
    source_info = SourceInfo.from_dict(make_source_info_dict())
    assert source_info.filename == "FILENAME"
    assert source_info.flow == "FLOW"
    check_meta(source_info.metadata)


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

