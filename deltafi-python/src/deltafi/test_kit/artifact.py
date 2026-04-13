#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

"""
Test-friendly artifact handle for unit testing actions that use the artifact registry.

Create from a literal string::

    handle = TestArtifactHandle.from_string('{"key": "value"}')

From raw bytes::

    handle = TestArtifactHandle.from_bytes(b'\\x00\\x01\\x02')

To exercise on_update callbacks registered by the code under test::

    handle.fire_update(VersionChangedEvent(...))
"""

import io
from typing import Callable, Dict, List, Optional

from deltafi.artifact import Artifact, ArtifactManifest, VersionChangedEvent, _accepts_args


class TestArtifactHandle(Artifact):
    """
    In-memory artifact handle for tests.  No network, no cache manager.

    Pass directly to action constructors in place of the ``Artifact`` default::

        action = MyAction(model=TestArtifactHandle.from_string("test data"))
    """
    __test__ = False  # prevent pytest from collecting this as a test class

    def __init__(
        self,
        data: bytes,
        *,
        artifact_name: str = "test-artifact",
        version: Optional[str] = "1.0",
        description: Optional[str] = None,
        content_type: Optional[str] = None,
        sha256: Optional[str] = None,
        labels: Optional[Dict[str, str]] = None,
        source_prefix: str = "plugins/test",
        ready: bool = True,
    ):
        super().__init__(artifact_name)
        self._data = data
        self._version = version
        self._description = description
        self._content_type = content_type
        self._sha256 = sha256
        self._labels = labels
        self._source_prefix = source_prefix
        self._ready = ready
        self._callbacks: List[Callable] = []

    # ── Factory classmethods ─────────────────────────────────────────────────

    @classmethod
    def from_string(cls, content: str, **kwargs) -> "TestArtifactHandle":
        """Create a handle whose content is the UTF-8 encoding of *content*."""
        return cls(content.encode("utf-8"), **kwargs)

    @classmethod
    def from_bytes(cls, data: bytes, **kwargs) -> "TestArtifactHandle":
        """Create a handle backed by the given raw bytes."""
        return cls(bytes(data), **kwargs)

    # ── Artifact interface ───────────────────────────────────────────────────

    def get_bytes(self) -> bytes:
        return self._data

    def get_input_stream(self) -> io.BytesIO:
        return io.BytesIO(self._data)

    def get_string(self, encoding: str = "utf-8") -> str:
        return self._data.decode(encoding)

    def stream(self):
        raise NotImplementedError("Streaming is not supported in TestArtifactHandle")

    def get_manifest(self) -> ArtifactManifest:
        return ArtifactManifest(
            self._version, self._description, self._content_type,
            self._sha256, self._labels,
        )

    def get_version(self) -> Optional[str]:
        return self._version

    def get_description(self) -> Optional[str]:
        return self._description

    def get_content_type(self) -> Optional[str]:
        return self._content_type

    def get_labels(self) -> Optional[Dict[str, str]]:
        return self._labels

    def get_sha256(self) -> Optional[str]:
        return self._sha256

    def get_source_prefix(self) -> Optional[str]:
        return self._source_prefix

    def is_ready(self) -> bool:
        return self._ready

    def is_default(self) -> bool:
        return self._labels is not None and self._labels.get("default") == "true"

    def is_shared(self) -> bool:
        return self._source_prefix == "shared"

    def refresh(self):
        pass  # no-op — content is fixed at construction time

    def on_update(self, callback: Callable) -> "TestArtifactHandle":
        self._callbacks.append(callback)
        return self

    # ── Test helpers ─────────────────────────────────────────────────────────

    def fire_update(self, event: VersionChangedEvent):
        """Invoke all on_update callbacks with *event*, simulating a version change."""
        for cb in self._callbacks:
            if _accepts_args(cb):
                cb(event)
            else:
                cb()
