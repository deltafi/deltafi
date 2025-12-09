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

from abc import ABC, abstractmethod
from enum import Enum
import json
import os
from typing import List

import requests

from deltafi.graphql import GraphQLExecutor
from deltafi.types import PluginCoordinates


class LookupTable:
    def __init__(self, name: str, columns: List[str], key_columns: List[str], source_plugin: PluginCoordinates = None,
                 service_backed: bool = True, backing_service_active: bool = True, pull_through: bool = False,
                 refresh_duration: str = None, last_refresh: str = None):
        self.name = name
        self.source_plugin = source_plugin
        self.columns = columns
        self.key_columns = key_columns
        self.service_backed = service_backed
        self.backing_service_active = backing_service_active
        self.pull_through = pull_through
        self.refresh_duration = refresh_duration
        self.last_refresh = last_refresh

    @classmethod
    def from_dict(cls, lookup_table: dict):
        return LookupTable(name=lookup_table.get('name'), source_plugin=lookup_table.get('sourcePlugin'),
            columns=lookup_table.get('columns'), key_columns=lookup_table.get('keyColumns'),
            service_backed=lookup_table.get('serviceBacked'),
            backing_service_active=lookup_table.get('backingServiceActive'),
            pull_through=lookup_table.get('pullThrough'),refresh_duration=lookup_table.get('refreshDuration'),
            last_refresh=lookup_table.get('lastRefresh'))

    def json(self):
        source_plugin = None
        if self.source_plugin is not None:
            source_plugin = self.source_plugin.json()

        return {
            'name': self.name,
            'sourcePlugin': source_plugin,
            'columns': self.columns,
            'keyColumns': self.key_columns,
            'serviceBacked': self.service_backed,
            'backingServiceActive': self.backing_service_active,
            'pullThrough': self.pull_through,
            'refreshDuration': self.refresh_duration,
            'lastRefresh': self.last_refresh
        }


class LookupTableEvent:
    @classmethod
    def create(cls, event: dict):
        return LookupTableEvent(id=event.get('id'), lookup_table_name=event.get('lookupTableName'),
            matching_column_values=event.get('matchingColumnValues'), result_columns=event.get('resultColumns'),
            variables=event.get('variables'))

    def __init__(self, id: str, lookup_table_name: str, matching_column_values: dict, result_columns: List[str],
                 variables: dict):
        self.id = id
        self.lookup_table_name = lookup_table_name
        self.matching_column_values = matching_column_values
        self.result_columns = result_columns
        self.variables = variables


class LookupTableEventResult:
    def __init__(self, lookup_table_event_id: str, lookup_table_name: str, rows: List[dict]):
        self.lookup_table_event_id = lookup_table_event_id
        self.lookup_table_name = lookup_table_name
        self.rows = rows

    def json(self):
        return {
            'lookupTableEventId': self.lookup_table_event_id,
            'lookupTableName': self.lookup_table_name,
            'rows': self.rows
        }


class SortDirection(Enum):
    ASC = "ASC"
    DESC = "DESC"


class LookupOptions:
    @classmethod
    def default_lookup_options(cls):
        return LookupOptions()

    def __init__(self, matching_column_values: dict = None, result_columns: List[str] = None, sort_column: str = None,
            sort_direction: SortDirection = None, offset: int = None, limit: int = None):
        self.matching_column_values = matching_column_values
        self.result_columns = result_columns
        self.sort_column = sort_column
        self.sort_direction = sort_direction
        self.offset = offset
        self.limit = limit


class LookupResults:
    def __init__(self, total_count: int, results: List[dict]):
        self.total_count = total_count
        self.results = results


class UploadFileType(Enum):
    JSON = "application/json"
    CSV = "text/csv"


class LookupTableClient:
    def __init__(self):
        self.core_url = os.getenv('CORE_URL', 'http://deltafi-core:8080') + '/api/v2'
        self.common_headers = {
            'X-User-Permissions': 'Admin',
            'X-User-Name': 'deltafi-cli'
        }
        self.graphql_executor = GraphQLExecutor(self.core_url, self.common_headers)

    def create_lookup_table(self, lookup_table: LookupTable):
        if lookup_table.refresh_duration is None:
            refresh_duration_value = "null"
        else:
            refresh_duration_value = f"\"{lookup_table.refresh_duration}\""
        mutation = f"""
        mutation createLookupTable {{
          createLookupTable(
            lookupTableInput: {{name: "{lookup_table.name}", columns: [{quoted_string_list(lookup_table.columns)}],
              keyColumns: [{quoted_string_list(lookup_table.key_columns)}], serviceBacked: {to_graphql_boolean(lookup_table.service_backed)},
              backingServiceActive: {to_graphql_boolean(lookup_table.backing_service_active)}, pullThrough: {to_graphql_boolean(lookup_table.pull_through)},
              refreshDuration: {refresh_duration_value}}}
          ) {{
            success
            info
            errors
          }}
        }}"""

        return self.graphql_executor.execute_query('createLookupTable', mutation)

    def get_lookup_tables(self):
        query = """
        query getLookupTables {
          getLookupTables {
            name
            columns
            keyColumns
            serviceBacked
            backingServiceActive
            pullThrough
            refreshDuration
            lastRefresh
          }
        }"""

        response_dict = self.graphql_executor.execute_query('getLookupTables', query)

        return [LookupTable.from_dict(lookup_table) for lookup_table in response_dict]

    def lookup(self, lookup_table_name: str, lookup_options: LookupOptions):
        lookup_args = f"lookupTableName: \"{lookup_table_name}\""
        if lookup_options.matching_column_values is not None:
            lookup_args += f"\n            matchingColumnValues: [{to_graphql(lookup_options.matching_column_values)}]"
        if lookup_options.result_columns is not None:
            lookup_args += f"\n            resultColumns: [{quoted_string_list(lookup_options.result_columns)}]"
        if lookup_options.sort_column is not None:
            lookup_args += f"\n            sortColumn: {lookup_options.sort_column}"
        if lookup_options.sort_direction is not None:
            lookup_args += f"\n            sortDirection: {lookup_options.sort_direction}"
        if lookup_options.offset is not None:
            lookup_args += f"\n            offset: {lookup_options.offset}"
        if lookup_options.limit is not None:
            lookup_args += f"\n            limit: {lookup_options.limit}"

        query = f"""
        query lookup {{
          lookup(
            {lookup_args}
          ) {{
            totalCount
            rows {{
              column
              value
            }}
          }}
        }}"""

        response_dict = self.graphql_executor.execute_query('lookup', query)

        return LookupResults(response_dict['totalCount'],
            [to_dict(column_values) for column_values in response_dict['rows']])

    def upload_table(self, lookup_table_name: str, file_type: UploadFileType, file_contents: str):
        headers = self.common_headers.copy()
        headers['Content-Type'] = file_type.value
        response = requests.post(f"{self.core_url}/lookup/{lookup_table_name}", headers=headers, data=file_contents)
        if not response.ok:
            raise RuntimeError(f"Unable to upload table {lookup_table_name}: Server returned status code {response.status_code}: {response.text}")


def to_graphql(matching_column_values: dict):
    matching_column_value_array = []
    for key, value in matching_column_values.items():
        matching_column_value_array.append(f"{{column: \"{key}\", value: [{quoted_string_list(value)}]}}")
    return ', '.join(matching_column_value_array)


def to_graphql_boolean(boolean):
    if boolean is True:
        return 'true'
    else:
        return 'false'


def quoted_string_list(strings: List[str]):
    return ', '.join('"' + s + '"' for s in strings)


def to_dict(column_values: List[dict]):
    row_dict = {}
    for column_value in column_values:
        row_dict[column_value.get('column')] = column_value.get('value')
    return row_dict


class LookupTableSupplier(ABC):
    def __init__(self, lookup_table_client: LookupTableClient, lookup_table: LookupTable):
        self.lookup_table_client = lookup_table_client
        self.lookup_table = lookup_table

    @abstractmethod
    def get_rows(self, variables: dict, matching_column_value: dict = None, result_columns: List[str] = None):
        pass

    def upload_table(self, variables: dict):
        self.upload_table_of_type(UploadFileType.JSON, json.dumps(self.get_rows(variables)))

    def upload_table_of_type(self, upload_file_type: UploadFileType, file: str):
        self.lookup_table_client.upload_table(self.lookup_table.name, upload_file_type, file)


class ResourceLookupTableSupplier(LookupTableSupplier, ABC):
    def __init__(self, lookup_table_client: LookupTableClient, lookup_table: LookupTable, path: str):
        super().__init__(lookup_table_client, lookup_table)
        self.path = path

    def get_rows(self, variables: dict, matching_column_value: dict = None, result_columns: List[str] = None):
        self.upload_table(variables)
        return []

    def upload_table(self, variables: dict):
        with open(self.path, 'r') as file:
            file_contents = file.read()

        if self.path.endswith('.csv'):
            self.upload_table_of_type(UploadFileType.CSV, file_contents)
        else:
            self.upload_table_of_type(UploadFileType.JSON, file_contents)
