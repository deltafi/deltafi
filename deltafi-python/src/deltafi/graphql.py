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

import json
import requests


class GraphQLExecutor:
    def __init__(self, core_url: str, headers: dict):
        self.graphql_url = f"{core_url}/graphql"
        self.graphql_headers = headers.copy()
        self.graphql_headers['Content-Type'] = 'application/json'

    def execute_query(self, query_name: str, query: str):
        response = requests.post(self.graphql_url, headers=self.graphql_headers, json={"query": query})
        if not response.ok:
            raise RuntimeError(f"Error executing GraphQL query: {response.text}\n\nOriginal query:\n{query}")

        result = json.loads(response.text)
        if "errors" in result:
            errors = ""
            for error in result["errors"]:
                errors += error["message"] + "\n"
            raise RuntimeError(f"Error executing GraphQL query: {errors}\nOriginal query:\n{query}")

        return result['data'][query_name]
