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


import deltafi.domain
import pkg_resources
import requests
from deltafi.action import DomainAction, LoadAction
from deltafi.actioneventqueue import ActionEventQueue
from deltafi.domain import Context
from deltafi.input import DomainInput, LoadInput
from deltafi.plugin import Plugin
from deltafi.result import DomainResult, LoadResult
from deltafi.storage import ContentService
from mockito import when, mock, unstub, verifyStubbedInvocationsAreUsed
from pydantic import BaseModel, Field
from requests.models import Response


class SampleDomainAction(DomainAction):
    def __init__(self):
        super().__init__('Domain action description', ['domain1', 'domain2'])

    def domain(self, context: Context, params: BaseModel, domain_input: DomainInput):
        return DomainResult().index_metadata('theIndexMetaKey', 'theIndexMetaValue')


class SampleLoadParameters(BaseModel):
    domain: str = Field(description="The domain used by the load action")


class SampleLoadAction(LoadAction):
    def __init__(self):
        super().__init__('Domain action description')

    def param_class(self):
        return SampleLoadParameters

    def load(self, context: Context, params: SampleLoadParameters, load_input: LoadInput):
        return LoadResult().add_metadata('loadKey', 'loadValue') \
            .add_domain(params.domain, 'Python domain!', 'text/plain') \
            .add_content('loaded content', new_content_reference)


def test_plugin(monkeypatch):
    unstub()

    monkeypatch.setenv("PROJECT_GROUP", "plugin-group")
    monkeypatch.setenv("PROJECT_NAME", "project-name")
    monkeypatch.setenv("PROJECT_VERSION", "1.0.0")

    core_url = "http://thehostcore"
    monkeypatch.setenv("CORE_URL", core_url)
    monkeypatch.setenv("REDIS_PASSWORD", "redis-password")
    monkeypatch.setenv("MINIO_ACCESSKEY", "minio-access")
    monkeypatch.setenv("MINIO_SECRETKEY", "minio-secret")

    monkeypatch.setenv("COMPUTERNAME", "my-conputer")

    queue = mock(ActionEventQueue)
    content_service = mock(ContentService)

    when(deltafi.plugin).ActionEventQueue(...).thenReturn(queue)
    when(deltafi.plugin).ContentService(...).thenReturn(content_service)

    expected_kit_version = pkg_resources.get_distribution('deltafi').version

    expected_url = core_url + "/plugins"
    expected_headers = {'Content-type': 'application/json'}
    expected_json = {
        "pluginCoordinates": {
            "groupId": "plugin-group",
            "artifactId": "project-name",
            "version": "1.0.0"
        },
        "displayName": "project-name",
        "description": "plugin-description",
        "actionKitVersion": expected_kit_version,
        "dependencies": [

        ],
        "actions": [
            {
                "name": "plugin-group.SampleDomainAction",
                "description": "Domain action description",
                "type": "DOMAIN",
                "requiresDomains": [
                    "domain1",
                    "domain2"
                ],
                "requiresEnrichments": [

                ],
                "schema": {
                    "title": "BaseModel",
                    "type": "object",
                    "properties": {

                    }
                }
            },
            {
                "name": "plugin-group.SampleLoadAction",
                "description": "Domain action description",
                "type": "LOAD",
                "requiresDomains": [

                ],
                "requiresEnrichments": [

                ],
                "schema": {
                    "title": "SampleLoadParameters",
                    "type": "object",
                    "properties": {
                        "domain": {
                            "title": "Domain",
                            "description": "The domain used by the load action",
                            "type": "string"
                        }
                    },
                    "required": [
                        "domain"
                    ]
                }
            }
        ],
        "variables": [

        ],
        "flowPlans": [

        ]
    }

    mock_response = mock(Response)
    mock_response.ok = True
    mock_response.status_code = 200
    mock_response.content = "content"

    when(requests).post(expected_url,
                        headers=expected_headers,
                        json=expected_json).thenReturn(mock_response)

    plugin = Plugin([SampleDomainAction, SampleLoadAction], "plugin-description")
    plugin._register()
    verifyStubbedInvocationsAreUsed(queue)
    verifyStubbedInvocationsAreUsed(content_service)
