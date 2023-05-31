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
from deltafi.exception import MissingMetadataException, ExpectedContentException, MissingDomainException, \
    MissingEnrichmentException


class DomainInput(NamedTuple):
    content: List[Content]
    metadata: Dict[str, str]
    domains: Dict[str, Domain]

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


class EgressInput(NamedTuple):
    content: Content
    metadata: dict


class EnrichInput(NamedTuple):
    content: List[Content]
    metadata: dict
    domains: Dict[str, Domain]
    enrichments: Dict[str, Domain]

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
        return name in self.enrichments

    def enrichment(self, name: str) -> Domain:
        if not self.has_enrichment(name):
            raise MissingEnrichmentException(name)
        return self.enrichments[name]


class FormatInput(NamedTuple):
    content: List[Content]
    metadata: dict
    domains: Dict[str, Domain]
    enrichments: Dict[str, Domain]

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
        return name in self.enrichments

    def enrichment(self, name: str) -> Domain:
        if not self.has_enrichment(name):
            raise MissingEnrichmentException(name)
        return self.enrichments[name]


class LoadInput(NamedTuple):
    content: List[Content]
    metadata: dict

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
    content: List[Content]
    metadata: dict

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
    content: Content
    metadata: dict
