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
from deltafi.result import DomainResult, EgressResult, EnrichResult, ErrorResult, FilterResult, FormatResult, \
    FormatManyResult, LoadResult, ReinjectResult, TransformResult, ValidateResult, ChildFormatResult

from .helperutils import make_context, make_segment


def verify_metric(metric, name, value, tags):
    assert metric['name'] == name
    assert metric['value'] == value
    assert metric['tags'] == tags


def verify_no_metrics(result):
    metrics = [metric.json() for metric in result.metrics]
    assert len(metrics) == 0


def test_domain_result():
    result = DomainResult(make_context())
    assert result.result_key == "domain"
    assert result.result_type == "DOMAIN"
    response = result.response()
    annotations = response.get('annotations')
    assert len(annotations) == 0

    result.annotate("key1", "value1")
    response = result.response()
    assert len(response.items()) == 1
    annotations = response.get('annotations')
    assert len(annotations) == 1
    assert annotations.get("key1") == "value1"

    verify_no_metrics(result)
    result.add_metric(Metric("test_metric", 100, {"tag": "val"}))
    metrics = [metric.json() for metric in result.metrics]
    assert len(metrics) == 1
    verify_metric(metrics[0], "test_metric", 100, {'tag': 'val'})


def test_egress_result():
    result = EgressResult(make_context(), "urlOut", 123)
    assert result.result_key is None
    assert result.result_type == "EGRESS"
    assert result.response() is None
    metrics = [metric.json() for metric in result.metrics]
    assert len(metrics) == 2
    verify_metric(metrics[0], "files_out", 1, {'endpoint': 'urlOut'})
    verify_metric(metrics[1], "bytes_out", 123, {'endpoint': 'urlOut'})


def test_enrich_result():
    result = EnrichResult(make_context())
    result.enrich("enrichmentName", "enrichmentValue", "mediaType")
    assert result.result_key == "enrich"
    assert result.result_type == "ENRICH"

    response = result.response()
    assert len(response.items()) == 2
    annotations = response.get('annotations')
    assert len(annotations) == 0

    result.annotate("key1", "value1")
    response = result.response()
    annotations = response.get('annotations')
    assert len(annotations) == 1
    assert annotations.get("key1") == "value1"

    enrichments = response.get('enrichments')
    assert len(enrichments) == 1
    assert enrichments[0] == {
        'name': "enrichmentName",
        'value': "enrichmentValue",
        'mediaType': "mediaType"}
    verify_no_metrics(result)


def test_error_result():
    result = ErrorResult(None, "errorCause", "errorContext")
    assert result.result_key == "error"
    assert result.result_type == "ERROR"
    verify_no_metrics(result)
    response = result.response()
    assert len(response.items()) == 2
    assert response.get('cause') == "errorCause"
    assert response.get('context') == "errorContext"


def test_filter_result():
    result = FilterResult(None, "filteredCause")
    assert result.result_key == "filter"
    assert result.result_type == "FILTER"
    verify_no_metrics(result)
    response = result.response()
    assert len(response.items()) == 1
    assert response.get('message') == "filteredCause"


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


def make_format_result(context, file_name, seg_id):
    result = FormatResult(context)
    result.set_content(make_content(context.content_service, file_name, seg_id))
    add_canned_metadata(result)
    return result


def verify_format_result(response, file_name, seg_id):
    assert len(response.items()) == 3
    assert response.get('content')['name'] == file_name
    assert response.get('content')['segments'][0]['uuid'] == seg_id
    verify_all_metadata(response)


def test_format_result():
    result = make_format_result(make_context(), "filename1", "id1")
    assert result.result_key == "format"
    assert result.result_type == "FORMAT"
    verify_no_metrics(result)
    verify_format_result(result.response(), "filename1", "id1")


def verify_child_format_result(response, file_name, seg_id):
    assert len(response.items()) == 4
    did = response.get("did")
    assert len(did) == 36
    assert response.get('content')['name'] == file_name
    assert response.get('content')['segments'][0]['uuid'] == seg_id
    verify_all_metadata(response)


def test_format_many_result():
    result = FormatManyResult(make_context())
    child_result1 = ChildFormatResult(make_format_result(make_context(), "fn1", "id1"))
    result.add_format_result(child_result1)
    result.add_format_result(make_format_result(make_context(), "fn2", "id2"))
    assert result.result_key == "formatMany"
    assert result.result_type == "FORMAT_MANY"
    verify_no_metrics(result)

    responses = result.response()
    assert len(responses) == 2
    verify_child_format_result(responses[0], "fn1", "id1")
    verify_child_format_result(responses[1], "fn2", "id2")


def make_content(content_service, name, seg_id):
    content = Content(name=name, segments=[make_segment(seg_id)], media_type="xml", content_service=content_service)
    return content


def test_load_result():
    result = LoadResult(make_context())
    result.add_content(make_content(None, "content1", "id1"))
    result.add_content(make_content(None, "content2", "id2"))
    add_canned_metadata(result)
    result.add_domain("domain1", "data1", "xml", )
    result.add_domain("domain2", "data2", "json", )
    result.annotate('a', 'b')
    result.delete_metadata_key('delete1')
    assert result.result_key == "load"
    assert result.result_type == "LOAD"
    verify_no_metrics(result)

    response = result.response()
    assert len(response) == 5
    verify_all_metadata(response)
    content = response.get("content")
    assert len(content) == 2
    assert content[0]['name'] == "content1"
    assert content[1]['name'] == "content2"
    assert response['annotations'] == {'a': 'b'}

    domains = response.get("domains")
    assert len(domains) == 2
    assert domains[0] == {
        'name': "domain1",
        'value': "data1",
        'mediaType': "xml"}
    assert domains[1] == {
        'name': "domain2",
        'value': "data2",
        'mediaType': "json"}

    assert response.get('annotations') == {'a': 'b'}
    assert response.get('deleteMetadataKeys') == ['delete1']


def test_reinject_result():
    result = ReinjectResult(make_context())
    result.add_child("fn1", "flow", [make_content(None, "content1", "id1")], {})
    result.add_child("fn2", "flow", [make_content(None, "content2", "id2")], {})
    assert result.result_key == "reinject"
    assert result.result_type == "REINJECT"
    verify_no_metrics(result)

    response = result.response()
    assert len(response) == 2
    assert len(response[0]['content']) == 1
    assert response[0]['content'][0]['segments'][0]['uuid'] == "id1"
    assert response[0]['filename'] == "fn1"
    assert response[0]['flow'] == "flow"

    assert len(response[1]['content']) == 1
    assert response[1]['content'][0]['segments'][0]['uuid'] == "id2"
    assert response[1]['filename'] == "fn2"
    assert response[1]['flow'] == "flow"


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


def test_validate_result():
    result = ValidateResult(make_context())
    assert result.result_key is None
    assert result.result_type == "VALIDATE"
    verify_no_metrics(result)
    assert result.result_key is None
