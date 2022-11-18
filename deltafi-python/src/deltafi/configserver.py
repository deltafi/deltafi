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
import os
import re
import sys
from typing import Any

import requests

from deltafi.logger import get_logger


def _make_key(property_set, key):
    return property_set + ":" + key


class ConfigServerClient:
    def __init__(self, url: str):
        self.url = url + "/application/default"
        self.config_map = {}
        self.logger = get_logger()

    def sync(self) -> None:
        """Query and save properties from config server"""
        try:
            response = requests.get(self.url)
            if response.ok:
                output = response.json()
                self.__parse__(output)
            else:

                self.logger.error(f"Failed to retrieve the configuration from {self.url} ({response.status_code}):\n"
                                  f"{response.content}")
                sys.exit(1)

        except RuntimeError as e:
            self.logger.error(f"Failed to retrieve the configuration from {self.url}:\n{e}")
            sys.exit(1)

    def deltafi_common(self, key):
        return self._lookup("deltafi-common", key)

    def action_kit(self, key):
        return self._lookup("action-kit", key)

    def _lookup(self, property_set, key) -> Any:
        compound_key = _make_key(property_set, key)
        value = self.config_map.get(compound_key)
        if value is not None:
            pattern = "\\$\\{(.*)\\}"
            result = re.match(pattern, value)
            if result:
                env_name = result.group(1)
                env_value = os.getenv(env_name)
                if not env_value:
                    self.logger.error("ENV not found: " + env_name)
                return env_value
        return value

    def __parse__(self, output):
        prop_sources = output.get("propertySources")
        self.config_map.clear()
        for src in prop_sources:
            name = src.get('name').split()[0]
            properties = src.get('source')
            keys = properties.keys()
            for key in keys:
                compound_key = _make_key(name, key)
                # Preserve override value, if present
                # TODO: This might not work if the override is null
                if self.config_map.get(compound_key) is None:
                    self.config_map.update({compound_key: properties.get(key)})
