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

from deltafi.domain import Content
from deltafi.metric import Metric
from deltafi.storage import ContentReference

ENDPOINT_TAG = "endpoint"
FILES_OUT = "files_out"
BYTES_OUT = "bytes_out"


class Result:
    __metaclass__ = abc.ABCMeta

    def __init__(self, result_key, result_type):
        self.result_key = result_key
        self.result_type = result_type
        self.metrics = []

    @abc.abstractmethod
    def response(self):
        pass

    def add_metric(self, metric: Metric):
        self.metrics.append(metric)


class DomainResult(Result):
    def __init__(self):
        super().__init__('domain', 'DOMAIN')
        self.indexed_metadata = {}

    def index_metadata(self, key: str, value: str):
        self.indexed_metadata[key] = value
        return self

    def response(self):
        return {
            'indexedMetadata': self.indexed_metadata
        }


class EgressResult(Result):
    def __init__(self, destination: str, bytes_egressed: int):
        super().__init__(None, 'EGRESS')
        self.add_metric(Metric(FILES_OUT, 1, {ENDPOINT_TAG: destination}))
        self.add_metric(Metric(BYTES_OUT, bytes_egressed, {ENDPOINT_TAG: destination}))

    def response(self):
        return None


class EnrichResult(Result):
    def __init__(self):
        super().__init__('enrich', 'ENRICH')
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
    def __init__(self, cause: str, context: str):
        super().__init__('error', 'ERROR')
        self.cause = cause
        self.context = context

    def response(self):
        return {
            'cause': self.cause,
            'context': self.context
        }


class FilterResult(Result):
    def __init__(self, filtered_cause: str):
        super().__init__('filter', 'FILTER')
        self.filtered_cause = filtered_cause

    def response(self):
        return {
            'message': self.filtered_cause
        }


class FormatResult(Result):
    def __init__(self, filename: str, content_reference: ContentReference):
        super().__init__('format', 'FORMAT')
        self.filename = filename
        self.content_reference = content_reference
        self.metadata = {}

    def add_metadata(self, key: str, value: str):
        self.metadata[key] = value
        return self

    def response(self):
        return {
            'filename': self.filename,
            'contentReference': self.content_reference.json(),
            'metadata': self.metadata
        }


class FormatManyResult(Result):
    def __init__(self):
        super().__init__('formatMany', 'FORMAT_MANY')
        self.format_results = []

    def add_format_result(self, format_result: FormatResult):
        self.format_results.append(format_result)
        return self

    def response(self):
        return [format_result.response() for format_result in self.format_results]


class LoadResult(Result):
    def __init__(self):
        super().__init__('load', 'LOAD')
        self.content = []
        self.metadata = {}
        self.domains = []

    def add_content(self, name: str, content_reference: ContentReference):
        content = Content(name=name, metadata={}, content_reference=content_reference)
        self.content.append(content)
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


class SplitResult(Result):
    class SplitChild:
        def __init__(self, filename: str, flow: str, metadata: Dict[str, str], content: List[Content]):
            self.filename = filename
            self.flow = flow
            self.metadata = metadata
            self.content = content

        def json(self):
            return {
                'sourceInfo': {
                    'filename': self.filename,
                    'flow': self.flow,
                    'metadata': self.metadata
                },
                'content': [content.json() for content in self.content]
            }

    def __init__(self):
        super().__init__('split', 'SPLIT')
        self.children = []

    def add_child(self, filename: str, flow: str, metadata: Dict[str, str], content: List[Content]):
        child = SplitResult.SplitChild(filename, flow, metadata, content)
        self.children.append(child)

    def response(self):
        return [child.json() for child in self.children]


class TransformResult(Result):
    def __init__(self):
        super().__init__('transform', 'TRANSFORM')
        self.content = []
        self.metadata = {}

    def add_content(self, name: str, content_reference: ContentReference):
        content = Content(name=name, metadata={}, content_reference=content_reference)
        self.content.append(content)
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
    def __init__(self):
        super().__init__(None, 'VALIDATE')

    def response(self):
        return None
