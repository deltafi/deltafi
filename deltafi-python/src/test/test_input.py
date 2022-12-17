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
import pytest
from deltafi.domain import Event
from deltafi.exception import MissingMetadataException, MissingSourceMetadataException
from deltafi.input import DomainInput
from deltafi.storage import ContentService
from mockito import mock, unstub

from .helperutils import *


def make_event(content_service):
    logger = None
    event = Event.create({
        'deltaFile': make_delta_file_dict(),
        'actionContext': make_context_dict(),
        'actionParams': {}
    },
        "HOSTNAME", content_service, logger)
    return event


def test_domain_input():
    unstub()
    mock_content_service = mock(ContentService)
    event = make_event(mock_content_service)

    input = DomainInput(source_filename=event.delta_file.source_info.filename,
                        ingress_flow=event.delta_file.source_info.flow,
                        source_metadata=event.delta_file.source_info.metadata,
                        metadata=event.delta_file.protocol_stack[-1].metadata,
                        domains={domain.name: domain for domain in event.delta_file.domains})

    assert input.get_source_metadata("key1") == "value1"
    assert input.get_source_metadata_or_else("keyX", "not-found") == "not-found"
    with pytest.raises(MissingSourceMetadataException):
        input.get_source_metadata("keyX")

    assert input.get_metadata("plKey1") == "valueA"
    assert input.get_metadata_or_else("plkeyX", "not-found") == "not-found"
    with pytest.raises(MissingMetadataException):
        input.get_metadata("plkeyX")
