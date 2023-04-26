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

from deltafi.domain import Content, Context, Domain, Event
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


def test_event():
    unstub()
    mock_content_service = mock(ContentService)
    logger = None
    event = Event.create({
        'deltaFileMessage': make_delta_file_message_dict(),
        'actionContext': make_context_dict(),
        'actionParams': {}
    },
        "HOSTNAME", mock_content_service, logger)

    assert event.context.did == TEST_DID
    assert event.context.ingress_flow == "IN"
    assert event.context.content_service == mock_content_service
    assert event.context.logger is None
    assert len(event.params) == 0
