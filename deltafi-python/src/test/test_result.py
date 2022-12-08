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

from deltafi.result import EgressResult


def test_egress_result():
    result = EgressResult("urlOut", 123)
    metrics = [metric.json() for metric in result.metrics]

    assert len(metrics) == 2
    assert metrics[0]['name'] == "files_out"
    assert metrics[0]['value'] == 1
    assert metrics[0]['tags'][0]["key"] == "endpoint"
    assert metrics[0]['tags'][0]["value"] == "urlOut"

    assert metrics[1]['name'] == "bytes_out"
    assert metrics[1]['value'] == 123
    assert metrics[1]['tags'][0]["key"] == "endpoint"
    assert metrics[1]['tags'][0]["value"] == "urlOut"
