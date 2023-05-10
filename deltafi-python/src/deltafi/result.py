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

import abc
from typing import Dict, List
import uuid

from deltafi.domain import Content, Context, SourceInfo
from deltafi.metric import Metric

ENDPOINT_TAG = "endpoint"
FILES_OUT = "files_out"
BYTES_OUT = "bytes_out"


class Result:
    __metaclass__ = abc.ABCMeta

    def __init__(self, result_key, result_type, context):
        self.result_key = result_key
        self.result_type = result_type
        self.metrics = []
        self.context = context

    @abc.abstractmethod
    def response(self):
        pass

    def add_metric(self, metric: Metric):
        self.metrics.append(metric)


class DomainResult(Result):
    def __init__(self, context: Context):
        super().__init__('domain', 'DOMAIN', context)
        self.indexed_metadata = {}

    def index_metadata(self, key: str, value: str):
        self.indexed_metadata[key] = value
        return self

    def response(self):
        return {
            'indexedMetadata': self.indexed_metadata
        }


class EgressResult(Result):
    def __init__(self, context: Context, destination: str, bytes_egressed: int):
        super().__init__(None, 'EGRESS', context)
        self.add_metric(Metric(FILES_OUT, 1, {ENDPOINT_TAG: destination}))
        self.add_metric(Metric(BYTES_OUT, bytes_egressed, {ENDPOINT_TAG: destination}))

    def response(self):
        return None


class EnrichResult(Result):
    def __init__(self, context: Context):
        super().__init__('enrich', 'ENRICH', context)
        self.enrichments = []
        self.indexed_metadata = {}

    def enrich(self, name: str, value: str, media_type: str):
        self.enrichments.append({
            'name': name,
            'value': value,
            'mediaType': media_type
        })
        return self

    def index_metadata(self, key: str, value: str):
        self.indexed_metadata[key] = value
        return self

    def response(self):
        return {
            'enrichments': self.enrichments,
            'indexedMetadata': self.indexed_metadata
        }


class ErrorResult(Result):
    def __init__(self, context: Context, error_cause: str, error_context: str):
        super().__init__('error', 'ERROR', context)
        self.error_cause = error_cause
        self.error_context = error_context

    def response(self):
        return {
            'cause': self.error_cause,
            'context': self.error_context
        }


class FilterResult(Result):
    def __init__(self, context: Context, filtered_cause: str):
        super().__init__('filter', 'FILTER', context)
        self.filtered_cause = filtered_cause

    def response(self):
        return {
            'message': self.filtered_cause
        }


class FormatResult(Result):
    def __init__(self, context: Context):
        super().__init__('format', 'FORMAT', context)
        self.content = None
        self.metadata = {}

    def add_metadata(self, key: str, value: str):
        self.metadata[key] = value
        return self

    def set_content(self, content: Content):
        self.content = content
        return self

    def save_string_content(self, string_data: str, name: str, media_type: str):
        content_reference = self.context.content_service.put_str(self.context.did, string_data, media_type)
        self.content = Content(name=name, content_reference=content_reference, content_service=self.context.content_service)
        return self

    def save_byte_content(self, byte_data: bytes, name: str, media_type: str):
        content_reference = self.context.content_service.put_bytes(self.context.did, byte_data, media_type)
        self.content = Content(name=name, content_reference=content_reference, content_service=self.context.content_service)
        return self

    def response(self):
        return {
            'filename': self.content.name,
            'contentReference': self.content.content_reference.json(),
            'metadata': self.metadata
        }


class FormatManyResult(Result):
    def __init__(self, context: Context):
        super().__init__('formatMany', 'FORMAT_MANY', context)
        self.format_results = []

    def add_format_result(self, format_result: FormatResult):
        self.format_results.append(format_result)
        return self

    def response(self):
        return [format_result.response() for format_result in self.format_results]


class LoadResult(Result):
    def __init__(self, context: Context):
        super().__init__('load', 'LOAD', context)
        self.content = []
        self.metadata = {}
        self.domains = []

    # content can be a single Content or a List[Content]
    def add_content(self, content):
        if content:
            if type(content) == list:
                self.content.extend(content)
            else:
                self.content.append(content)

        return self

    def save_string_content(self, string_data: str, name: str, media_type: str):
        content_reference = self.context.content_service.put_str(self.context.did, string_data, media_type)
        self.content.append(Content(name=name, content_reference=content_reference, content_service=self.context.content_service))
        return self

    def save_byte_content(self, byte_data: bytes, name: str, media_type: str):
        content_reference = self.context.content_service.put_bytes(self.context.did, byte_data, media_type)
        self.content.append(Content(name=name, content_reference=content_reference, content_service=self.context.content_service))
        return self

    def add_metadata(self, key: str, value: str):
        self.metadata[key] = value
        return self

    def add_domain(self, name: str, value: str, media_type: str):
        self.domains.append({
            'name': name,
            'value': value,
            'mediaType': media_type})
        return self

    def response(self):
        return {
            'domains': self.domains,
            'protocolLayer': {
                'content': [content.json() for content in self.content],
                'metadata': self.metadata
            }
        }


class ChildLoadResult:
    def __init__(self, load_result: LoadResult = None):
        self._did = str(uuid.uuid4())
        self.load_result = load_result

    @property
    def did(self):
        return self._did

    def response(self):
        res = self.load_result.response()
        res["did"] = self._did
        return res


class LoadManyResult(Result):
    def __init__(self, context: Context):
        super().__init__('loadMany', 'LOAD_MANY', context)
        self.load_results = []

    def add_load_result(self, load_result):
        if isinstance(load_result, ChildLoadResult):
            self.load_results.append(load_result)
        else:
            self.load_results.append(ChildLoadResult(load_result))
        return self

    def response(self):
        return [load_result.response() for load_result in self.load_results]


class ReinjectResult(Result):
    class ReinjectChild:
        def __init__(self, source_info: SourceInfo, content: List[Content]):
            self.source_info = source_info
            self.content = content

        def json(self):
            return {
                'sourceInfo': self.source_info.json(),
                'content': [content.json() for content in self.content]
            }

    def __init__(self, context: Context):
        super().__init__('reinject', 'REINJECT', context)
        self.children = []

    def add_child(self, filename: str, flow: str, metadata: Dict[str, str], content: List[Content]):
        child = ReinjectResult.ReinjectChild(SourceInfo(filename, flow, metadata), content)
        self.children.append(child)

    def response(self):
        return [child.json() for child in self.children]


class TransformResult(Result):
    def __init__(self, context: Context):
        super().__init__('transform', 'TRANSFORM', context)
        self.content = []
        self.metadata = {}

    # content can be a single Content or a List[Content]
    def add_content(self, content):
        if content:
            if type(content) == list:
                self.content.extend(content)
            else:
                self.content.append(content)

        return self

    def save_string_content(self, string_data: str, name: str, media_type: str):
        content_reference = self.context.content_service.put_str(self.context.did, string_data, media_type)
        self.content.append(Content(name=name, content_reference=content_reference, content_service=self.context.content_service))
        return self

    def save_byte_content(self, byte_data: bytes, name: str, media_type: str):
        content_reference = self.context.content_service.put_bytes(self.context.did, byte_data, media_type)
        self.content.append(Content(name=name, content_reference=content_reference, content_service=self.context.content_service))
        return self

    def add_metadata(self, key: str, value: str):
        self.metadata[key] = value
        return self

    def response(self):
        return {
            'protocolLayer': {
                'content': [content.json() for content in self.content],
                'metadata': self.metadata
            }
        }


class ValidateResult(Result):
    def __init__(self, context: Context):
        super().__init__(None, 'VALIDATE', context)

    def response(self):
        return None
