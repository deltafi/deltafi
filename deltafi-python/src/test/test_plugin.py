#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
from unittest import mock

from deltafi.plugin import Plugin, PluginCoordinates

from .sample.actions import SampleTransformAction


def test_plugin_registration_json():
    plugin_coordinates = PluginCoordinates("plugin-group", "project-name", "1.0.0")
    plugin = Plugin("plugin-description", plugin_name="project-name",
                    plugin_coordinates=plugin_coordinates,
                    actions=[SampleTransformAction])

    print("NARDO")
    print(get_expected_json())
    print("NARDO 2")
    print(plugin.registration_json())
    assert get_expected_json() == plugin.registration_json()


def test_plugin_registration_json_from_env(monkeypatch):
    monkeypatch.setenv("PROJECT_GROUP", "plugin-group")
    monkeypatch.setenv("PROJECT_NAME", "project-name")
    monkeypatch.setenv("PROJECT_VERSION", "1.0.0")

    plugin = Plugin("plugin-description", actions=[SampleTransformAction])

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
                "name": "plugin-group.SampleTransformAction",
                "description": "Transform action description",
                "type": "TRANSFORM",
                "supportsJoin": False,
                "schema": {
                    "title": "SampleTransformParameters",
                    "type": "object",
                    "properties": {
                        "a_string": {
                            "title": "A String",
                            "description": "this string parameter is required",
                            "type": "string"
                        },
                        "def_string": {
                            "title": "Def String",
                            "default": "default-val",
                            "description": "str with default",
                            "type": "string"
                        },
                        "a_dict": {
                            "additionalProperties": {
                                "type": "string"
                            },
                            "title": "A Dict",
                            "description": "this dict parameter is required",
                            "type": "object"
                        },
                        "def_dict": {
                            "additionalProperties": {
                                "type": "string"
                            },
                            "title": "Def Dict",
                            "description": "dict has default",
                            "default": ["key1:val1"],
                            "type": "object"
                        },
                        "a_list": {
                            "items": {
                                "type": "string"
                            },
                            "title": "A List",
                            "description": "list with default",
                            "default": [],
                            "type": "array"
                        },
                        "a_bool": {
                            "title": "A Bool",
                            "description": "this boolean parameter is required",
                            "type": "boolean"
                        },
                        "def_int": {
                            "title": "Def Int",
                            "description": "int with default",
                            "default": 100,
                            "type": "integer"
                        }
                    },
                    "required": [
                        "a_string",
                        "a_dict",
                        "a_bool"
                    ]
                },
                "docsMarkdown": None
            }
        ],
        "variables": [

        ],
        "flowPlans": [

        ]
    }
