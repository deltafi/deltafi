#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
from deltafi.exception import MissingMetadataException, ExpectedContentException

class EgressInput(NamedTuple):
    content: Content
    metadata: dict


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
