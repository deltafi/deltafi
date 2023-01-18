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

from deltafi.domain import *
from deltafi.exception import MissingMetadataException, MissingSourceMetadataException, ExpectedContentException, \
    MissingDomainException, MissingEnrichmentException


class DomainInput(NamedTuple):
    source_filename: str
    ingress_flow: str
    source_metadata: Dict[str, str]
    metadata: Dict[str, str]
    domains: Dict[str, Domain]

    def get_source_metadata(self, key: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            raise MissingSourceMetadataException(key)

    def get_source_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            return default

    def get_metadata(self, key: str):
        if key in self.metadata:
            return self.metadata[key]
        else:
            raise MissingMetadataException(key)

    def get_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.metadata:
            return self.metadata[key]
        else:
            return default

    def has_domain(self, name: str) -> bool:
        return name in self.domains

    def domain(self, name: str) -> Domain:
        if not self.has_domain(name):
            raise MissingDomainException(name)
        return self.domains[name]


class EgressInput(NamedTuple):
    source_filename: str
    ingress_flow: str
    source_metadata: Dict[str, str]
    formatted_data: FormattedData

    def get_source_metadata(self, key: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            raise MissingSourceMetadataException(key)

    def get_source_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            return default


class EnrichInput(NamedTuple):
    source_filename: str
    ingress_flow: str
    source_metadata: Dict[str, str]
    content: List[Content]
    metadata: dict
    domains: Dict[str, Domain]
    enrichment: Dict[str, Domain]

    def get_source_metadata(self, key: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            raise MissingSourceMetadataException(key)

    def get_source_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            return default

    def has_content(self) -> bool:
        return len(self.content) > 0

    def content_at(self, index: int) -> Content:
        if len(self.content) < index + 1:
            raise ExpectedContentException(index, len(self.content))
        return self.content[index]

    def first_content(self):
        return self.content_at(0)

    def get_metadata(self, key: str):
        if key in self.metadata:
            return self.metadata[key]
        else:
            raise MissingMetadataException(key)

    def get_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.metadata:
            return self.metadata[key]
        else:
            return default

    def has_domain(self, name: str) -> bool:
        return name in self.domains

    def domain(self, name: str) -> Domain:
        if not self.has_domain(name):
            raise MissingDomainException(name)
        return self.domains[name]

    def has_enrichment(self, name: str) -> bool:
        return name in self.enrichment

    def enrichment(self, name: str) -> Domain:
        if not self.has_enrichment(name):
            raise MissingEnrichmentException(name)
        return self.enrichment[name]


class FormatInput(NamedTuple):
    source_filename: str
    ingress_flow: str
    source_metadata: Dict[str, str]
    content: List[Content]
    metadata: dict
    domains: Dict[str, Domain]
    enrichment: Dict[str, Domain]

    def get_source_metadata(self, key: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            raise MissingSourceMetadataException(key)

    def get_source_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            return default

    def has_content(self) -> bool:
        return len(self.content) > 0

    def content_at(self, index: int) -> Content:
        if len(self.content) < index + 1:
            raise ExpectedContentException(index, len(self.content))
        return self.content[index]

    def first_content(self):
        return self.content_at(0)

    def get_metadata(self, key: str):
        if key in self.metadata:
            return self.metadata[key]
        else:
            raise MissingMetadataException(key)

    def get_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.metadata:
            return self.metadata[key]
        else:
            return default

    def has_domain(self, name: str) -> bool:
        return name in self.domains

    def domain(self, name: str) -> Domain:
        if not self.has_domain(name):
            raise MissingDomainException(name)
        return self.domains[name]

    def has_enrichment(self, name: str) -> bool:
        return name in self.enrichment

    def enrichment(self, name: str) -> Domain:
        if not self.has_enrichment(name):
            raise MissingEnrichmentException(name)
        return self.enrichment[name]


class LoadInput(NamedTuple):
    source_filename: str
    ingress_flow: str
    source_metadata: Dict[str, str]
    content: List[Content]
    metadata: dict

    def get_source_metadata(self, key: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            raise MissingSourceMetadataException(key)

    def get_source_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            return default

    def has_content(self) -> bool:
        return len(self.content) > 0

    def content_at(self, index: int) -> Content:
        if len(self.content) < index + 1:
            raise ExpectedContentException(index, len(self.content))
        return self.content[index]

    def first_content(self):
        return self.content_at(0)

    def get_metadata(self, key: str):
        if key in self.metadata:
            return self.metadata[key]
        else:
            raise MissingMetadataException(key)

    def get_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.metadata:
            return self.metadata[key]
        else:
            return default


class TransformInput(NamedTuple):
    source_filename: str
    ingress_flow: str
    source_metadata: Dict[str, str]
    content: List[Content]
    metadata: dict

    def get_source_metadata(self, key: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            raise MissingSourceMetadataException(key)

    def get_source_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            return default

    def has_content(self) -> bool:
        return len(self.content) > 0

    def content_at(self, index: int) -> Content:
        if len(self.content) < index + 1:
            raise ExpectedContentException(index, len(self.content))
        return self.content[index]

    def first_content(self):
        return self.content_at(0)

    def get_metadata(self, key: str):
        if key in self.metadata:
            return self.metadata[key]
        else:
            raise MissingMetadataException(key)

    def get_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.metadata:
            return self.metadata[key]
        else:
            return default


class ValidateInput(NamedTuple):
    source_filename: str
    ingress_flow: str
    source_metadata: Dict[str, str]
    formatted_data: FormattedData

    def get_source_metadata(self, key: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            raise MissingSourceMetadataException(key)

    def get_source_metadata_or_else(self, key: str, default: str) -> str:
        if key in self.source_metadata:
            return self.source_metadata[key]
        else:
            return default
