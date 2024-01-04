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

from deltafi.domain import Content
from deltafi.metric import Metric
from deltafi.result import EgressResult, ErrorResult, TransformResult

from .helperutils import make_context, make_segment


def verify_metric(metric, name, value, tags):
    assert metric['name'] == name
    assert metric['value'] == value
    assert metric['tags'] == tags


def verify_no_metrics(result):
    metrics = [metric.json() for metric in result.metrics]
    assert len(metrics) == 0


def test_egress_result():
    result = EgressResult(make_context(), "urlOut", 123)
    assert result.result_key is None
    assert result.result_type == "EGRESS"
    assert result.response() is None
    metrics = [metric.json() for metric in result.metrics]
    assert len(metrics) == 2
    verify_metric(metrics[0], "files_out", 1, {'endpoint': 'urlOut'})
    verify_metric(metrics[1], "bytes_out", 123, {'endpoint': 'urlOut'})


def test_error_result():
    result = ErrorResult(None, "errorCause", "errorContext")
    result.annotate('a', 'b')
    assert result.result_key == "error"
    assert result.result_type == "ERROR"
    verify_no_metrics(result)
    response = result.response()
    assert len(response.items()) == 3
    assert response.get('cause') == "errorCause"
    assert response.get('context') == "errorContext"
    assert response['annotations'] == {'a': 'b'}


def verify_all_metadata(item):
    metadata = item.get("metadata")
    assert len(metadata) == 3
    assert metadata["key1"] == "val1"
    assert metadata["key2"] == "val2"
    assert metadata["key3"] == "val3"


def add_canned_metadata(result):
    result.add_metadata("key1", "val1")
    result.add_metadata("key2", "val2")
    result.add_metadata("key3", "val3")


def make_content(content_service, name, seg_id):
    content = Content(name=name, segments=[make_segment(seg_id)], media_type="xml", content_service=content_service)
    return content


def test_transform_result():
    result = TransformResult(make_context())
    add_canned_metadata(result)
    result.add_content(make_content(None, "content1", "id1"))
    result.add_content(make_content(None, "content2", "id2"))
    result.annotate('a', 'b')
    result.delete_metadata_key('delete1')
    result.delete_metadata_key('delete2')

    assert result.result_key == "transform"
    assert result.result_type == "TRANSFORM"
    verify_no_metrics(result)

    response = result.response()
    assert len(response) == 4
    verify_all_metadata(response)
    content = response.get("content")
    assert len(content) == 2
    assert content[0]['name'] == "content1"
    assert content[1]['name'] == "content2"
    assert response.get("annotations") == {'a': 'b'}
    assert response.get('deleteMetadataKeys') == ['delete1', 'delete2']
