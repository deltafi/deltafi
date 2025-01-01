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

import importlib
import inspect
import json
import os
import pkgutil
import sys
import threading
import time
import traceback
from datetime import datetime, timezone, timedelta
from importlib import metadata
from os.path import isdir, isfile, join
from pathlib import Path
from typing import List

import requests
import yaml
from deltafi.action import Action, Join
from deltafi.actioneventqueue import ActionEventQueue
from deltafi.domain import Event, ActionExecution
from deltafi.exception import ExpectedContentException, MissingMetadataException
from deltafi.logger import get_logger
from deltafi.result import ErrorResult, IngressResult, TransformResult, TransformResults
from deltafi.storage import ContentService


def _coordinates():
    return PluginCoordinates(os.getenv('PROJECT_GROUP'), os.getenv('PROJECT_NAME'), os.getenv('PROJECT_VERSION'))


def _valid_file(filename: str):
    return isfile(filename) and \
        (filename.endswith(".json")
         or filename.endswith(".yaml")
         or filename.endswith(".yml"))


def _read_valid_files(path: str):
    """
    Read the contents of a directory, and returns a filtered list of files
    that can be read/parsed for plugin usage, and ignores everything else.
    :param path: name of the directory to scan
    :return: list of filtered, parsable files
    """
    files = []
    if isdir(path):
        files = [f for f in os.listdir(path) if _valid_file(join(path, f))]
    return files


def _load_resource(path: str, filename: str):
    """
    Read the content of a JSON or YAML file, and return a Python
    object of its contents, typically as a dict or list.
    To avoid exceptions, use only files returned by _read_valid_files().
    :param path: directory which contains the file to load
    :param filename: name of the file to load
    :return: dict or list of file contents
    """
    with open(join(path, filename)) as file_in:
        if filename.endswith(".json"):
            return json.load(file_in)
        elif filename.endswith(".yaml") or filename.endswith(".yml"):
            results = []
            yaml_docs = yaml.safe_load_all(file_in)
            for doc_iter in yaml_docs:
                # yaml_docs must be iterated
                results.append(doc_iter)
            if len(results) == 1:
                # Single document YAML file
                return results[0]
            else:
                # Multi-document YAML file
                return results
    raise RuntimeError(f"File type not supported: {filename}")


def _load__all_resource(path: str, file_list: List[str]):
    resources = []
    for f in file_list:
        r = _load_resource(path, f)
        if isinstance(r, list):
            resources.extend(r)
        else:
            resources.append(r)
    return resources


def _find_variables_filename(names: List[str]):
    if 'variables.json' in names:
        return 'variables.json'
    elif 'variables.yaml' in names:
        return 'variables.yaml'
    elif 'variables.yml' in names:
        return 'variables.yml'
    else:
        return None


def _setup_queue(max_connections):
    url = os.getenv('VALKEY_URL', 'http://deltafi-valkey-master:6379')
    password = os.getenv('VALKEY_PASSWORD')
    return ActionEventQueue(url, max_connections, password)


def _setup_content_service():
    minio_url = os.getenv('MINIO_URL', 'http://deltafi-minio:9000')
    return ContentService(minio_url,
                          os.getenv('MINIO_ACCESSKEY'),
                          os.getenv('MINIO_SECRETKEY'))


class PluginCoordinates(object):
    def __init__(self, group_id: str, artifact_id: str, version: str):
        self.group_id = group_id
        self.artifact_id = artifact_id
        self.version = version

    def __json__(self):
        return {
            "groupId": self.group_id,
            "artifactId": self.artifact_id,
            "version": self.version
        }


LONG_RUNNING_TASK_DURATION = timedelta(seconds=5)


class Plugin(object):
    def __init__(self, description: str, plugin_name: str = None, plugin_coordinates: PluginCoordinates = None,
                 actions: List = None, action_package: str = None):
        """
        Initialize the plugin object
        :param plugin_name: Name of the plugin project
        :param description: Description of the plugin
        :param plugin_coordinates: plugin coordinates of the plugin, if None the coordinates must be defined in
                                   environment variables
        :param actions: list of action classes to run
        :param action_package: name of the package containing the actions to run
        """
        self.logger = get_logger()

        self.content_service = None
        self.queue = None
        self.actions = []
        self.core_url = os.getenv('CORE_URL')
        self.image = os.getenv('IMAGE')
        self.image_pull_secret = os.getenv('IMAGE_PULL_SECRET')
        action_classes = []
        if actions is not None and len(actions):
            action_classes.extend(actions)

        if action_package is not None:
            found_actions = Plugin.find_actions(action_package)
            if len(found_actions):
                action_classes.extend(found_actions)

        unique_actions = dict.fromkeys(action_classes)
        self.actions = [action() for action in unique_actions]

        self.description = description
        self.display_name = os.getenv('PROJECT_NAME') if plugin_name is None else plugin_name
        self.coordinates = _coordinates() if plugin_coordinates is None else plugin_coordinates

        if os.getenv('ACTIONS_HOSTNAME'):
            self.hostname = os.getenv('ACTIONS_HOSTNAME')
        elif os.getenv('HOSTNAME'):
            self.hostname = os.getenv('HOSTNAME')
        elif os.getenv('COMPUTERNAME'):
            self.hostname = os.getenv('COMPUTERNAME')
        else:
            self.hostname = 'UNKNOWN'

        self.logger.debug(f"Initialized ActionRunner with actions {self.actions}")

    @staticmethod
    def find_actions(package_name) -> List[object]:
        """
        Find all concrete classes that extend the base Action class in the given package
        :param package_name: name of the package to load and scan for actions
        :return: list of classes that extend the Action class
        """
        package = importlib.import_module(package_name)
        classes = []
        visited = set()

        # Iterate over all submodules in the package
        for _, module_name, _ in pkgutil.walk_packages(package.__path__):
            try:
                module = importlib.import_module(package.__name__ + '.' + module_name)
            except ModuleNotFoundError:
                continue

            # Iterate over all members in the module
            for name, obj in inspect.getmembers(module):
                if inspect.isclass(obj) and obj.__module__.startswith(package_name) and obj not in visited:
                    if Plugin.is_action(obj):
                        classes.append(obj)
                    visited.add(obj)

        return classes

    @staticmethod
    def is_action(maybe_action: type) -> bool:
        """
        Check if the given object is a non-abstract subclass of the Action class
        :param maybe_action: object to inspect to see if it is an Action class
        :return: true if the object is a non-abstract subclass of the Action class
        """
        return not inspect.isabstract(maybe_action) and issubclass(maybe_action, Action)

    def action_name(self, action):
        return f"{self.coordinates.group_id}.{action.__class__.__name__}"

    def _load_action_docs(self, action):
        docs_path = str(Path(os.path.dirname(os.path.abspath(sys.argv[0]))) / 'docs')
        if not isdir(docs_path):
            return None

        action_docs_file = join(docs_path, action.__class__.__name__ + '.md')
        if not isfile(action_docs_file):
            return None

        return open(action_docs_file).read()

    def _action_json(self, action):
        return {
            'name': self.action_name(action),
            'description': action.description,
            'type': action.action_type.name,
            'supportsJoin': isinstance(action, Join),
            'schema': action.param_class().model_json_schema(),
            'docsMarkdown': self._load_action_docs(action)
        }

    @staticmethod
    def load_integration_tests(tests_path: str):
        test_files = _read_valid_files(tests_path)
        return _load__all_resource(tests_path, test_files)

    @staticmethod
    def load_variables(flows_path: str, flow_files: List[str]):
        variables = []
        variables_filename = _find_variables_filename(flow_files)
        if variables_filename is not None:
            flow_files.remove(variables_filename)
            variables = _load__all_resource(flows_path, [variables_filename])
        return variables

    def registration_json(self):
        flows_path = str(Path(os.path.dirname(os.path.abspath(sys.argv[0]))) / 'flows')
        tests_path = str(Path(os.path.dirname(os.path.abspath(sys.argv[0]))) / 'integration')

        variables = []
        flow_files = _read_valid_files(flows_path)
        if len(flow_files) == 0:
            self.logger.warning(
                f"Flows directory ({flows_path}) does not exist or contains no valid files. No flows will be installed.")
        else:
            variables = self.load_variables(flows_path, flow_files)

        flows = _load__all_resource(flows_path, flow_files)
        actions = [self._action_json(action) for action in self.actions]

        test_files = self.load_integration_tests(tests_path)
        if len(test_files) == 0:
            self.logger.warning(
                f"tests directory ({tests_path}) does not exist or contains no valid files. No tests will be installed.")

        return {
            'pluginCoordinates': self.coordinates.__json__(),
            'displayName': self.display_name,
            'description': self.description,
            'actionKitVersion': metadata.version('deltafi'),
            'image': self.image,
            'imagePullSecret': self.image_pull_secret,
            'dependencies': [],
            'actions': actions,
            'variables': variables,
            'flowPlans': flows,
            'integrationTests': test_files
        }

    def _register(self):
        url = f"{self.core_url}/plugins"
        headers = {'Content-type': 'application/json'}
        registration_json = self.registration_json()

        self.logger.info(f"Registering plugin:\n{registration_json}")

        response = requests.post(url, headers=headers, json=registration_json)
        if not response.ok:
            self.logger.error(f"Failed to register plugin ({response.status_code}):\n{response.content}")
            exit(1)

        self.logger.info("Plugin registered")

    def run(self):
        self.logger.info("Plugin starting")
        self.queue = _setup_queue(len(self.actions) + 1)
        self.content_service = _setup_content_service()
        self._register()
        for action in self.actions:
            threading.Thread(target=self._do_action, args=(action,)).start()

        hb_thread = threading.Thread(target=self._heartbeat)
        hb_thread.start()

        self.logger.info("All threads running")

        f = open("/tmp/running", "w")
        f.close()

        self.logger.info("Application initialization complete")
        hb_thread.join()

    def _heartbeat(self):
        long_running_actions = set()
        while True:
            try:
                # Set heartbeats
                for action in self.actions:
                    self.queue.heartbeat(self.action_name(action))

                # Record long running tasks
                new_long_running_actions = set()
                for action in self.actions:
                    if action.action_execution and action.action_execution.exceeds_duration(LONG_RUNNING_TASK_DURATION):
                        action_execution = action.action_execution
                        new_long_running_actions.add(action_execution)
                        self.queue.record_long_running_task(action_execution)

                # Remove old long running tasks
                tasks_to_remove = long_running_actions - new_long_running_actions
                for action_execution in tasks_to_remove:
                    self.queue.remove_long_running_task(action_execution)

                long_running_actions = new_long_running_actions

            except Exception as e:
                self.logger.error(f"Failed to register action queue heartbeat or record long running tasks: {e}", e)
            finally:
                time.sleep(10)

    @staticmethod
    def to_response(event, start_time, stop_time, result):
        response = {
            'did': event.context.did,
            'flowName': event.context.flow_name,
            'flowId': event.context.flow_id,
            'actionName': event.context.action_name,
            'start': start_time,
            'stop': stop_time,
            'type': result.result_type,
            'metrics': [metric.json() for metric in result.metrics]
        }
        if result.result_key is not None:
            response[result.result_key] = result.response()
        return response

    def _do_action(self, action):
        action_logger = get_logger(self.action_name(action))

        action_logger.info(f"Listening on {self.action_name(action)}")
        while True:
            try:
                event_string = self.queue.take(self.action_name(action))
                event = Event.create(json.loads(event_string), self.content_service, action_logger)
                start_time = time.time()
                action_logger.debug(f"Processing event for did {event.context.did}")

                action.action_execution = ActionExecution(self.action_name(action), event.context.action_name,
                                                          event.context.did, datetime.now(timezone.utc))

                try:
                    result = action.execute_action(event)
                except ExpectedContentException as e:
                    result = ErrorResult(event.context,
                                         f"Action attempted to look up element {e.index + 1} (index {e.index}) from "
                                         f"content list of size {e.size}",
                                         f"{str(e)}\n{traceback.format_exc()}")
                except MissingMetadataException as e:
                    result = ErrorResult(event.context,
                                         f"Missing metadata with key {e.key}",
                                         f"{str(e)}\n{traceback.format_exc()}")
                except BaseException as e:
                    result = ErrorResult(event.context,
                                         f"Action execution {type(e)} exception", f"{str(e)}\n{traceback.format_exc()}")

                action.action_execution = None

                response = Plugin.to_response(
                    event, start_time, time.time(), result)

                Plugin.orphaned_content_check(action_logger, event.context, result, response)

                topic = 'dgs'
                if event.return_address:
                    topic += f"-{event.return_address}"
                self.queue.put(topic, json.dumps(response))
            except BaseException as e:
                action_logger.error(f"Unexpected {type(e)} error: {str(e)}\n{traceback.format_exc()}")
                time.sleep(1)

    @staticmethod
    def orphaned_content_check(logger, context, result, response):
        if len(context.saved_content) > 0:
            to_delete = Plugin.find_unused_content(context.saved_content, result)
            if len(to_delete) > 0:
                errors = context.content_service.delete_all(to_delete)
                for e in errors:
                    logger.error(f"Unable to delete object(s), {e}")
                logger.warning(
                    f"Deleted {len(to_delete)} unused content entries for did {context.did} due to a {response['type']} event by {response['actionName']}")

    @staticmethod
    def find_unused_content(saved_content, result):
        segments_in_use = Plugin.used_segment_names(result)
        saved_segments = Plugin.get_segment_names(saved_content)
        to_delete = []
        for key, value in saved_segments.items():
            if key not in segments_in_use:
                to_delete.append(value)
        return to_delete

    @staticmethod
    def used_segment_names(result):
        segment_names = {}
        if isinstance(result, TransformResult):
            segment_names.update(result.get_segment_names())
        elif isinstance(result, TransformResults):
            segment_names.update(result.get_segment_names())
        elif isinstance(result, IngressResult):
            segment_names.update(result.get_segment_names())
        return segment_names

    @staticmethod
    def get_segment_names(content_list):
        segment_names = {}
        for content in content_list:
            segment_names.update(content.get_segment_names())
        return segment_names
