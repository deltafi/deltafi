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
import json
import os
import sys
import threading
import time
import traceback
from os.path import isdir, isfile, join
from pathlib import Path
from typing import List

import pkg_resources
import requests
from deltafi.actioneventqueue import ActionEventQueue
from deltafi.domain import Event
from deltafi.exception import ExpectedContentException, MissingDomainException, MissingEnrichmentException, \
    MissingSourceMetadataException, MissingMetadataException
from deltafi.logger import get_logger
from deltafi.result import ErrorResult
from deltafi.storage import ContentService


def _coordinates():
    return {
        'groupId': os.getenv('PROJECT_GROUP'),
        'artifactId': os.getenv('PROJECT_NAME'),
        'version': os.getenv('PROJECT_VERSION')
    }


def _setup_queue(max_connections):
    redis_url = os.getenv('REDIS_URL', 'http://deltafi-redis-master:6379')
    password = os.getenv('REDIS_PASSWORD')
    return ActionEventQueue(redis_url, max_connections, password)


def _setup_content_service():
    minio_url = os.getenv('MINIO_URL', 'http://deltafi-minio:9000')
    return ContentService(minio_url,
                          os.getenv('MINIO_ACCESSKEY'),
                          os.getenv('MINIO_SECRETKEY'))


class Plugin(object):
    def __init__(self, actions: List, description: str):
        self.actions = [action() for action in actions]
        self.description = description
        self.coordinates = _coordinates()

        self.core_url = os.getenv('CORE_URL')
        self.queue = _setup_queue(len(self.actions))
        self.content_service = _setup_content_service()

        if os.getenv('ACTIONS_HOSTNAME'):
            self.hostname = os.getenv('ACTIONS_HOSTNAME')
        elif os.getenv('HOSTNAME'):
            self.hostname = os.getenv('HOSTNAME')
        elif os.getenv('COMPUTERNAME'):
            self.hostname = os.getenv('COMPUTERNAME')
        else:
            self.hostname = 'UNKNOWN'

        self.logger = get_logger()

        self.logger.info(f"Initialized ActionRunner with actions {actions}")

    def action_name(self, action):
        return f"{self.coordinates['groupId']}.{action.__class__.__name__}"

    def _action_json(self, action):
        return {
            'name': self.action_name(action),
            'description': action.description,
            'type': action.action_type.name,
            'requiresDomains': action.requires_domains,
            'requiresEnrichments': action.requires_enrichments,
            'schema': action.param_class().schema()
        }

    def _register(self):
        flows_path = str(Path(os.path.dirname(os.path.abspath(sys.argv[0]))) / 'flows')

        flow_files = []
        variables = []
        if isdir(flows_path):
            flow_files = [f for f in os.listdir(flows_path) if isfile(join(flows_path, f))]
            if 'variables.json' in flow_files:
                flow_files.remove('variables.json')
                variables = json.load(open(join(flows_path, 'variables.json')))
        else:
            self.logger.warning(f"Flows directory ({flows_path}) does not exist. No flows will be installed.")

        flows = [json.load(open(join(flows_path, f))) for f in flow_files]
        actions = [self._action_json(action) for action in self.actions]

        url = f"{self.core_url}/plugins"
        headers = {'Content-type': 'application/json'}
        registration_json = \
            {
                'pluginCoordinates': self.coordinates,
                'displayName': os.getenv('PROJECT_NAME'),
                'description': self.description,
                'actionKitVersion': pkg_resources.get_distribution('deltafi').version,
                'dependencies': [],
                'actions': actions,
                'variables': variables,
                'flowPlans': flows
            }

        self.logger.info(f"Registering plugin:\n{registration_json}")

        response = requests.post(url, headers=headers, json=registration_json)
        if not response.ok:
            self.logger.error(f"Failed to register plugin ({response.status_code}):\n{response.content}")
            exit(1)

    def run(self):
        self._register()
        for action in self.actions:
            threading.Thread(target=self._do_action, args=(action,)).start()

    def _do_action(self, action):
        action_logger = get_logger(self.action_name(action))

        action_logger.info(f"Listening on {self.action_name(action)}")
        while True:
            try:
                event_string = self.queue.take(self.action_name(action))
                event = Event.create(json.loads(event_string), self.hostname, self.content_service, action_logger)
                action_logger.debug(f"Processing event for did {event.context.did}")

                try:
                    result = action.execute(event)
                except ExpectedContentException as e:
                    result = ErrorResult(f"Action attempted to look up element {e.index + 1} (index {e.index}) from "
                                         f"content list of size {e.size}",
                                         f"{str(e)}\n{traceback.format_exc()}")
                except MissingDomainException as e:
                    result = ErrorResult(f"Action attempted to access domain {e.name}, which does not exist",
                                         f"{str(e)}\n{traceback.format_exc()}")
                except MissingEnrichmentException as e:
                    result = ErrorResult(f"Action attempted to access enrichment {e.name}, which does not exist",
                                         f"{str(e)}\n{traceback.format_exc()}")
                except MissingSourceMetadataException as e:
                    result = ErrorResult(f"Missing ingress metadata with key {e.key}",
                                         f"{str(e)}\n{traceback.format_exc()}")
                except MissingMetadataException as e:
                    result = ErrorResult(f"Missing metadata with key {e.key}",
                                         f"{str(e)}\n{traceback.format_exc()}")
                except BaseException as e:
                    result = ErrorResult(f"Action execution {type(e)} exception", f"{str(e)}\n{traceback.format_exc()}")

                response = {
                    'did': event.context.did,
                    'action': event.context.action_name,
                    'start': time.time(),
                    'stop': time.time(),
                    'type': result.result_type,
                    'metrics': [metric.json() for metric in result.metrics]
                }
                if result.result_key is not None:
                    response[result.result_key] = result.response()

                self.queue.put('dgs', json.dumps(response))
            except BaseException as e:
                action_logger.error(f"Unexpected {type(e)} error: {str(e)}\n{traceback.format_exc()}")
                time.sleep(1)
