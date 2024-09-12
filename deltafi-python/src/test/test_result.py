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

from deltafi.domain import Content
from deltafi.result import EgressResult, ErrorResult, TransformResult, TransformResults

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
    assert len(response) == 1
    first = response[0]
    assert len(first) == 4
    verify_all_metadata(first)
    content = first.get("content")
    assert len(content) == 2
    assert content[0]['name'] == "content1"
    assert content[1]['name'] == "content2"
    assert first.get("annotations") == {'a': 'b'}
    assert first.get('deleteMetadataKeys') == ['delete1', 'delete2']


def test_transform_many_result():
    context = make_context()
    many_result = TransformResults(context)

    child1 = TransformResult(context)
    add_canned_metadata(child1)
    child1.add_content(make_content(None, "content1", "id1"))
    child1.add_content(make_content(None, "content2", "id2"))
    child1.annotate('a', 'b')
    child1.delete_metadata_key('delete1')
    child1.delete_metadata_key('delete2')

    many_result.add_result(child1, "name1")
    many_result.add_result(child1, )
    many_result.add_result(child1, "name3")

    assert many_result.result_key == "transform"
    assert many_result.result_type == "TRANSFORM"
    verify_no_metrics(many_result)

    response = many_result.response()

    print(response)

    assert len(response) == 3

    first = response[0]
    assert len(first) == 5
    assert "name" in first
    assert first.get("name") == "name1"

    verify_all_metadata(first)
    content = first.get("content")
    assert len(content) == 2
    assert content[0]['name'] == "content1"
    assert content[1]['name'] == "content2"
    assert first.get("annotations") == {'a': 'b'}
    assert first.get('deleteMetadataKeys') == ['delete1', 'delete2']

    second = response[1]
    assert len(second) == 4
    assert "name" not in second

    third = response[2]
    assert len(third) == 5
    assert "name" in third
    assert third.get("name") == "name3"
