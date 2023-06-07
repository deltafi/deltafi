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

from importlib import metadata
from deltafi.plugin import Plugin, PluginCoordinates
from unittest import mock
from .sample.actions import SampleDomainAction, SampleLoadAction


def test_plugin_registration_json():
    plugin_coordinates = PluginCoordinates("plugin-group", "project-name", "1.0.0")
    plugin = Plugin("plugin-description", plugin_name="project-name",
                    plugin_coordinates=plugin_coordinates,
                    actions=[SampleDomainAction, SampleLoadAction])

    assert get_expected_json() == plugin.registration_json()


def test_plugin_registration_json_from_env(monkeypatch):
    monkeypatch.setenv("PROJECT_GROUP", "plugin-group")
    monkeypatch.setenv("PROJECT_NAME", "project-name")
    monkeypatch.setenv("PROJECT_VERSION", "1.0.0")

    plugin = Plugin("plugin-description", actions=[SampleDomainAction, SampleLoadAction])

    assert get_expected_json() == plugin.registration_json()


def test_plugin_find_actions():
    plugin_coordinates = PluginCoordinates("plugin-group", "project-name", "1.0.0")
    plugin = Plugin("plugin-description", plugin_name="project-name",
                    plugin_coordinates=plugin_coordinates,
                    action_package="test.sample")

    assert get_expected_json() == plugin.registration_json()


def test_plugin_register(monkeypatch):
    monkeypatch.setenv("CORE_URL", "http://core")
    plugin_coordinates = PluginCoordinates("plugin-group", "project-name", "1.0.0")
    plugin = Plugin("plugin-description", plugin_name="project-name",
                    plugin_coordinates=plugin_coordinates,
                    action_package="test.sample")
    with mock.patch("requests.post") as mock_post:
        plugin._register()

    mock_post.assert_called_once_with("http://core/plugins",
                                      headers={'Content-type': 'application/json'},
                                      json=get_expected_json())


def get_expected_json():
    expected_kit_version = metadata.version('deltafi')
    return {
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