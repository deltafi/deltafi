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

from deltafi.domain import Content, Context, Event
from deltafi.storage import ContentService, Segment
from mockito import mock, unstub

from .helperutils import *


def check_meta(meta):
    assert meta["key1"] == "value1"
    assert meta["key2"] == "value2"


def check_pl_meta(meta):
    assert meta["plKey1"] == "valueA"
    assert meta["plKey2"] == "valueB"


def make_content(name, content_service):
    return Content.from_dict(make_content_dict(name), content_service)


def test_content_json():
    content = make_content("CONTENT_NAME", make_context())
    content_json = content.json()
    assert content_json["name"] == "CONTENT_NAME"


def test_content_copy():
    segment = Segment(uuid="id1", offset=0, size=100, did="device1")
    content = Content(name="test", segments=[segment], media_type="text/plain", content_service=mock(ContentService))
    copied_content = content.copy()
    assert content == copied_content
    assert content is not copied_content
    assert content.segments is not copied_content.segments


def test_content_subcontent():
    segment = Segment(uuid="id1", offset=0, size=100, did="device1")
    content = Content(name="test", segments=[segment], media_type="text/plain", content_service=mock(ContentService))
    sub_content = content.subcontent(50, 25)
    assert sub_content.name == content.name
    assert sub_content.content_service == content.content_service
    assert len(sub_content.segments) == 1
    assert sub_content.segments[0].offset == 50
    assert sub_content.segments[0].size == 25


def test_content_get_size():
    segment = Segment(uuid="id1", offset=0, size=100, did="device1")
    content = Content(name="test", segments=[segment], media_type="text/plain", content_service=mock(ContentService))
    assert content.get_size() == 100


def test_content_get_set_media_type():
    segment = Segment(uuid="id1", offset=0, size=100, did="device1")
    content = Content(name="test", segments=[segment], media_type="text/plain", content_service=mock(ContentService))
    assert content.get_media_type() == "text/plain"
    content.set_media_type("application/json")
    assert content.get_media_type() == "application/json"


def test_content_prepend():
    content_service = mock(ContentService)

    segment1 = Segment(uuid="id1", offset=0, size=100, did="device1")
    content1 = Content(name="test1", segments=[segment1], media_type="text/plain", content_service=content_service)

    segment2 = Segment(uuid="id2", offset=0, size=200, did="device2")
    content2 = Content(name="test2", segments=[segment2], media_type="text/plain", content_service=content_service)

    content1.prepend(content2)
    assert len(content1.segments) == 2
    assert content1.segments[0].uuid == "id2"
    assert content1.segments[1].uuid == "id1"
    assert content1.get_size() == 300


def test_content_append():
    content_service = mock(ContentService)

    segment1 = Segment(uuid="id1", offset=0, size=100, did="device1")
    content1 = Content(name="test1", segments=[segment1], media_type="text/plain", content_service=content_service)

    segment2 = Segment(uuid="id2", offset=0, size=200, did="device2")
    content2 = Content(name="test2", segments=[segment2], media_type="text/plain", content_service=content_service)

    content1.append(content2)
    assert len(content1.segments) == 2
    assert content1.segments[0].uuid == "id1"
    assert content1.segments[1].uuid == "id2"
    assert content1.get_size() == 300


def test_context_json():
    unstub()
    mock_content_service = mock(ContentService)
    logger = None
    context = Context.create(
        make_context_dict(),
        "HOSTNAME", mock_content_service, logger)

    assert context.did == TEST_DID
    assert context.action_flow == "ACTION_FLOW"
    assert context.action_name == "ACTION_NAME_IN_FLOW"
    assert context.source_filename == 'FILENAME'
    assert context.egress_flow == "OUT"
    assert context.system == "SYSTEM"
    assert context.hostname == "HOSTNAME"
    assert context.ingress_flow == "IN"
    assert context.content_service == mock_content_service
    assert context.collect is None
    assert context.collected_dids is None
    assert context.memo == 'note to self'
    assert context.logger is None


def test_event():
    unstub()
    mock_content_service = mock(ContentService)
    logger = None
    event = Event.create({
        'deltaFileMessages': [make_delta_file_message_dict()],
        'actionContext': make_context_dict(),
        'actionParams': {}
    },
        "HOSTNAME", mock_content_service, logger)

    assert event.context.did == TEST_DID
    assert event.context.action_flow == "ACTION_FLOW"
    assert event.context.action_name == "ACTION_NAME_IN_FLOW"
    assert event.context.ingress_flow == "IN"
    assert event.context.content_service == mock_content_service
    assert event.context.logger is None
    assert len(event.params) == 0
