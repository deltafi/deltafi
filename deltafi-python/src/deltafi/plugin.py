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
from datetime import datetime, timezone
from importlib import metadata
from os.path import isdir, isfile, join
from pathlib import Path
from typing import List, NamedTuple

import requests
import yaml

from deltafi.action import Action, Join
from deltafi.actioneventqueue import ActionEventQueue
from deltafi.domain import Event, ActionExecution
from deltafi.exception import ExpectedContentException, MissingMetadataException
from deltafi.logger import get_logger
from deltafi.lookuptable import LookupTable, LookupTableClient, LookupTableEvent, LookupTableEventResult, LookupTableSupplier
from deltafi.result import ErrorResult, IngressResult, TransformResult, TransformResults
from deltafi.storage import ContentService
from deltafi.types import PluginCoordinates


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
    url = os.getenv('VALKEY_URL', 'http://localhost:6379')
    password = os.getenv('VALKEY_PASSWORD')
    app_name = os.getenv('APP_NAME')
    return ActionEventQueue(url, max_connections, password, app_name)


def _setup_content_service():
    minio_url = os.getenv('MINIO_URL', 'http://localhost:9000')
    bucket_name = os.getenv('STORAGE_BUCKET_NAME', 'storage')
    return ContentService(minio_url,
                          os.getenv('MINIO_ACCESSKEY'),
                          os.getenv('MINIO_SECRETKEY'),
                          bucket_name)


class ActionThread(object):
    def __init__(self, clazz: Action, thread_num: int, name: str, execution: ActionExecution = None):
        self.clazz = clazz
        self.thread_num = thread_num
        self.name = name
        self.execution = execution

    def logger_name(self):
        return f"{self.name}#{self.thread_num}"




def build_error_context(e: BaseException):
    return f"{str(e)}\n\nTraceback (most recent call last):\n{'\n'.join(traceback.format_tb(e.__traceback__))}"


class Plugin(object):
    def __init__(self, description: str, plugin_name: str = None, plugin_coordinates: PluginCoordinates = None,
                 actions: List = None, action_package: str = None, lookup_table_suppliers_package: str = None,
                 thread_config: dict = None):
        """
        Initialize the plugin object
        :param plugin_name: Name of the plugin project
        :param description: Description of the plugin
        :param plugin_coordinates: plugin coordinates of the plugin, if None the coordinates must be defined in
                                   environment variables
        :param actions: list of action classes to run
        :param action_package: name of the package containing the actions to run
        :param lookup_table_suppliers_package name of the package containing lookup table suppliers
        :param thread_config: map of action class name and thread count. Actions not found default to 1 thread.
        """
        self.logger = get_logger()

        self.content_service = None
        self.queue = None
        self.singleton_actions = []
        self.action_threads = []
        self.thread_config = {}
        if thread_config is not None:
            self.thread_config = thread_config
        self.core_url = os.getenv('CORE_URL', 'http://127.0.0.1:8042')
        self.image = os.getenv('IMAGE')
        self.image_pull_secret = os.getenv('IMAGE_PULL_SECRET')
        action_classes = []
        if actions is not None and len(actions):
            action_classes.extend(actions)

        if action_package is not None:
            found_actions = Plugin.find_classes(action_package, Action)
            if len(found_actions):
                action_classes.extend(found_actions)

        unique_actions = dict.fromkeys(action_classes)
        self.singleton_actions = [action() for action in unique_actions]

        self.lookup_table_suppliers = {}
        if lookup_table_suppliers_package is not None:
            lookup_table_client = LookupTableClient()
            lookup_table_supplier_classes = Plugin.find_classes(lookup_table_suppliers_package, LookupTableSupplier)
            self.logger.info(f"Found {len(lookup_table_supplier_classes)} suppliers")
            for lookup_table_supplier_class in lookup_table_supplier_classes:
                lookup_table_supplier = lookup_table_supplier_class(lookup_table_client)
                self.lookup_table_suppliers[lookup_table_supplier.lookup_table.name] = lookup_table_supplier

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

        self.logger.debug(f"Initialized ActionRunner with actions {self.singleton_actions}")

    @staticmethod
    def find_classes(package_name: str, base_class: type) -> List[object]:
        """
        Find all concrete classes that extend the base class in the given package
        :param package_name: name of the package to load and scan
        :param base_class: the base class
        :return: list of classes that extend the base class
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
                    if not inspect.isabstract(obj) and issubclass(obj, base_class):
                        classes.append(obj)
                    visited.add(obj)

        return classes

    def action_name(self, action):
        return f"{self.coordinates.group_id}.{action.__class__.__name__}"

    def _action_json(self, action):
        return {
            'name': self.action_name(action),
            'type': action.action_type.name,
            'supportsJoin': isinstance(action, Join),
            'schema': action.param_class().model_json_schema(),
            'actionOptions': action.action_options.json()
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
        actions = [self._action_json(action) for action in self.singleton_actions]
        lookup_tables = [lookup_table_supplier.lookup_table.json() for lookup_table_supplier in self.lookup_table_suppliers.values()]

        test_files = self.load_integration_tests(tests_path)
        if len(test_files) == 0:
            self.logger.warning(
                f"tests directory ({tests_path}) does not exist or contains no valid files. No tests will be installed.")

        return {
            'pluginCoordinates': self.coordinates.json(),
            'displayName': self.display_name,
            'description': self.description,
            'actionKitVersion': metadata.version('deltafi'),
            'image': self.image,
            'imagePullSecret': self.image_pull_secret,
            'dependencies': [],
            'actions': actions,
            'lookupTables': lookup_tables,
            'variables': variables,
            'flowPlans': flows,
            'integrationTests': test_files
        }

    def _register(self):
        url = f"{self.core_url}/plugins"
        headers = {'Content-Type': 'application/json'}
        registration_json = self.registration_json()

        self.logger.info(f"Registering plugin:\n{registration_json}")

        response = requests.post(url, headers=headers, json=registration_json)
        if not response.ok:
            self.logger.error(f"Failed to register plugin ({response.status_code}):\n{response.content}")
            exit(1)

        self.logger.info("Plugin registered")

    def run(self):
        self.logger.info("Plugin starting")

        for action in self.singleton_actions:
            num_threads = 1
            if self.action_name(action) in self.thread_config:
                maybe_num_threads = self.thread_config[self.action_name(action)]
                if type(maybe_num_threads) is int and maybe_num_threads > 0:
                    num_threads = maybe_num_threads
                else:
                    self.logger.error(f"Ignoring non-int or invalid thread value {maybe_num_threads}")
            for i in range(num_threads):
                action_thread = ActionThread(action, i, self.action_name(action))
                self.action_threads.append(action_thread)

        total_threads = len(self.action_threads) + 1
        if len(self.lookup_table_suppliers) > 0:
            total_threads += 1

        self.queue = _setup_queue(total_threads)
        self.content_service = _setup_content_service()

        if len(self.lookup_table_suppliers) > 0:
            threading.Thread(target=self._handle_lookup_table_supplier_events).start()

        for action_thread in self.action_threads:
            threading.Thread(target=self._do_action, args=(action_thread,)).start()

        self._register()

        hb_thread = threading.Thread(target=self._heartbeat)
        hb_thread.start()

        self.logger.info("All threads running")

        f = open("/tmp/running", "w")
        f.close()

        self.logger.info("Application initialization complete")
        hb_thread.join()

    def _heartbeat(self):
        running_actions = set()
        while True:
            try:
                # Set heartbeats
                for action_thread in self.action_threads:
                    self.queue.heartbeat(action_thread.name)

                # Record running tasks
                new_running_actions = set()
                for action_thread in self.action_threads:
                    action_execution = action_thread.execution
                    if action_execution:
                        new_running_actions.add(action_execution)
                        self.queue.record_long_running_task(action_execution)

                # Remove completed tasks
                tasks_to_remove = running_actions - new_running_actions
                for action_execution in tasks_to_remove:
                    self.queue.remove_long_running_task(action_execution)

                running_actions = new_running_actions

            except Exception as e:
                self.logger.error(f"Failed to register action queue heartbeat or record running tasks: {e}", e)
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
            'messages': [message.json() for message in result.messages],
            'metrics': [metric.json() for metric in result.metrics]
        }
        if result.result_key is not None:
            response[result.result_key] = result.response()
        return response

    def _do_action(self, action_thread: ActionThread):
        action_logger = get_logger(action_thread.logger_name())
        action_logger.info(f"Listening on {action_thread.name}")

        while True:
            try:
                event_string = self.queue.take(action_thread.name)
                event = Event.create(json.loads(event_string), self.content_service, action_logger)
                start_time = time.time()
                action_logger.debug(f"Processing event for did {event.context.did}")

                action_thread.execution = ActionExecution(action_thread.name, event.context.action_name,
                                                          action_thread.thread_num, event.context.did,
                                                          datetime.now(timezone.utc))

                try:
                    result = action_thread.clazz.execute_action(event)
                except ExpectedContentException as e:
                    result = ErrorResult(event.context, f"Action attempted to look up element {e.index + 1} "
                        f"(index {e.index}) from content list of size {e.size}", build_error_context(e))
                except MissingMetadataException as e:
                    result = ErrorResult(event.context, f"Missing metadata with key {e.key}", build_error_context(e))
                except BaseException as e:
                    result = ErrorResult(event.context, f"Action execution {type(e)} exception", build_error_context(e))

                action_thread.execution = None

                response = Plugin.to_response(
                    event, start_time, time.time(), result)

                Plugin.orphaned_content_check(action_logger, event.context, result, response)

                topic = 'dgs'
                if event.return_address:
                    topic += f"-{event.return_address}"
                self.queue.put(topic, json.dumps(response))
            except BaseException as e:
                action_logger.error(f"Unexpected {type(e)} error: {build_error_context(e)}")
                time.sleep(1)

    def _handle_lookup_table_supplier_events(self):
        event_keys = ["lookup-table-event-" + key for key in self.lookup_table_suppliers.keys()]
        self.logger.debug(f"Listening for the following lookup table supplier events: {event_keys}")
        while True:
            event_string = self.queue.take(event_keys)
            lookup_table_event = LookupTableEvent.create(json.loads(event_string))
            rows = self.lookup_table_suppliers[lookup_table_event.lookup_table_name].get_rows(
                lookup_table_event.variables, lookup_table_event.matching_column_values,
                lookup_table_event.result_columns)
            lookup_table_event_result = LookupTableEventResult(lookup_table_event_id=lookup_table_event.id,
                lookup_table_name=lookup_table_event.lookup_table_name, rows=rows)
            self.queue.put(lookup_table_event.id, json.dumps(lookup_table_event_result.json()))

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
