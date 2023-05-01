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
from deltafi.action import DomainAction
from deltafi.domain import Context, Event
from deltafi.input import DomainInput
from deltafi.result import DomainResult, FilterResult
from deltafi.storage import ContentService
from mockito import mock, unstub
from pydantic import BaseModel

from .helperutils import *


class SampleDomainAction(DomainAction):
    def __init__(self):
        super().__init__('Description', ['TheDomain'])

    def domain(self, context: Context, params: BaseModel, domain_input: DomainInput):
        return DomainResult().index_metadata('theIndexMetaKey', 'theIndexMetaValue')


class InvalidResult(DomainAction):
    def __init__(self):
        super().__init__('Description', ['TheDomain'])

    def domain(self, context: Context, params: BaseModel, domain_input: DomainInput):
        return FilterResult("cause")


def make_event(content_service):
    logger = None
    event = Event.create({
        'deltaFileMessages': [make_delta_file_message_dict()],
        'actionContext': make_context_dict(),
        'actionParams': {}
    },
        "HOSTNAME", content_service, logger)
    return event


def test_domain_action():
    unstub()
    mock_content_service = mock(ContentService)

    action = SampleDomainAction()
    assert action.action_type.value == "domain"
    result = action.execute(make_event(mock_content_service))
    assert type(result) == DomainResult


def test_invalid_result():
    unstub()
    mock_content_service = mock(ContentService)

    action = InvalidResult()
    with pytest.raises(ValueError):
        result = action.execute(make_event(mock_content_service))
