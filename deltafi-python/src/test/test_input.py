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
from deltafi.domain import Event
from deltafi.exception import MissingMetadataException
from deltafi.input import DomainInput
from deltafi.storage import ContentService
from mockito import mock, unstub

from .helperutils import *


def make_event(content_service):
    logger = None
    event = Event.create({
        'deltaFileMessages': [make_delta_file_message_dict()],
        'actionContext': make_context_dict(),
        'actionParams': {}
    },
        "HOSTNAME", content_service, logger)
    return event


def test_domain_input():
    unstub()
    mock_content_service = mock(ContentService)
    event = make_event(mock_content_service)

    input = DomainInput(content=event.delta_file_messages[0].content_list,
                        metadata=event.delta_file_messages[0].metadata,
                        domains=event.delta_file_messages[0].domains)

    assert input.get_metadata("plKey1") == "valueA"
    assert input.get_metadata_or_else("plkeyX", "not-found") == "not-found"
    with pytest.raises(MissingMetadataException):
        input.get_metadata("plkeyX")
