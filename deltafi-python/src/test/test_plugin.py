#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

from deltafi.types import PluginCoordinates
from deltafi.plugin import Plugin

from .sample.actions import SampleTransformAction


def test_plugin_registration_json(monkeypatch):
    set_mock_env(monkeypatch)
    plugin_coordinates = PluginCoordinates("plugin-group", "project-name", "1.0.0")
    plugin = Plugin("plugin-description", plugin_name="project-name",
                    plugin_coordinates=plugin_coordinates,
                    actions=[SampleTransformAction])

    assert get_expected_json() == plugin.registration_json()


def test_plugin_registration_json_from_env(monkeypatch):
    monkeypatch.setenv("PROJECT_GROUP", "plugin-group")
    monkeypatch.setenv("PROJECT_NAME", "project-name")
    monkeypatch.setenv("PROJECT_VERSION", "1.0.0")
    set_mock_env(monkeypatch)

    plugin = Plugin("plugin-description", actions=[SampleTransformAction])

    assert get_expected_json() == plugin.registration_json()


def test_plugin_find_actions(monkeypatch):
    set_mock_env(monkeypatch)
    plugin_coordinates = PluginCoordinates("plugin-group", "project-name", "1.0.0")
    plugin = Plugin("plugin-description", plugin_name="project-name",
                    plugin_coordinates=plugin_coordinates,
                    action_package="test.sample")

    assert get_expected_json() == plugin.registration_json()


def check_results(results: list):
    found = 0
    for r in results:
        if r['name'] == "name1":
            assert r['description'] == 'd1'
            found += 1000
        elif r['name'] == "name2":
            assert r['description'] == 'd2'
            found += 100
        elif r['name'] == "name3":
            assert r['description'] == 'd3'
            found += 10
        elif r['name'] == "name4":
            assert r['description'] == 'd4'
            found += 1
    return found


def test_plugin_load_variables_single_doc(monkeypatch):
    set_mock_env(monkeypatch)
    variables = Plugin.load_variables("test/plugin_data/flows", ["variables.yaml"])
    assert len(variables) == 3
    assert check_results(variables) == 1110


def test_plugin_load_variables_multi_doc(monkeypatch):
    set_mock_env(monkeypatch)
    variables = Plugin.load_variables("test/plugin_data/flows", ["variables.yml"])
    assert len(variables) == 3
    assert check_results(variables) == 1110


def test_plugin_integration_tests(monkeypatch):
    set_mock_env(monkeypatch)
    results = Plugin.load_integration_tests("test/plugin_data/tests")
    assert len(results) == 4
    assert check_results(results) == 1111


def test_plugin_register(monkeypatch):
    set_mock_env(monkeypatch)
    plugin_coordinates = PluginCoordinates("plugin-group", "project-name", "1.0.0")
    plugin = Plugin("plugin-description", plugin_name="project-name",
                    plugin_coordinates=plugin_coordinates,
                    action_package="test.sample")
    with mock.patch("requests.post") as mock_post:
        plugin._register()

    mock_post.assert_called_once_with("http://core/plugins",
                                      headers={'Content-Type': 'application/json'},
                                      json=get_expected_json())


def set_mock_env(monkeypatch):
    monkeypatch.setenv("CORE_URL", "http://core")
    monkeypatch.setenv("IMAGE", "docker.io/group/plugin:1.0.0")
    monkeypatch.setenv("IMAGE_PULL_SECRET", "docker-creds")


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
        "image": "docker.io/group/plugin:1.0.0",
        "imagePullSecret": "docker-creds",
        "dependencies": [

        ],
        "actions": [
            {
                "name": "plugin-group.SampleTransformAction",
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
                "actionOptions": {
                    "description": "Transform action description",
                    "details": "The details",
                    "errors": [{
                        "conditions": ["Condition A", "Condition B"],
                        "description": "Error 1",
                    }, {
                        "description": "Error 2",
                    }],
                    "filters": [{
                        "conditions": ["Condition A", "Condition B"],
                        "description": "Filter 1",
                    }, {
                        "description": "Filter 2",
                    }],
                    "inputSpec": {
                        "contentSummary": "The input content summary",
                        "metadataSummary": "The input metadata summary",
                    },
                    "notes": ["Note 1", "Note2"],
                    "outputSpec": {
                        "annotationsSummary": "The output annotations summary",
                        "contentSummary": "The output content summary",
                        "metadataSummary": "The output metadata summary",
                        "passthrough": False,
                    },
                },
            }
        ],
        "lookupTables": [

        ],
        "variables": [

        ],
        "flowPlans": [

        ],
        "integrationTests": [

        ]
    }
