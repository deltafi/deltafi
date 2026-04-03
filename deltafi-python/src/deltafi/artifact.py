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
Artifact registry client and cache for DeltaFi Python plugins.

Usage (action author)::

    from deltafi.artifact import Artifact, ArtifactScope

    class MyAction(TransformAction):
        model  = Artifact("my-model", scope=ArtifactScope.PLUGIN_FIRST)
        lookup = Artifact("lookup-table", scope=ArtifactScope.SHARED_ONLY, alias="stable")

        def transform(self, context, params, transform_input):
            data = self.model.get_bytes()
            ...
"""

import inspect
import io
import json
import logging
import threading
import time
import uuid
from dataclasses import dataclass
from enum import Enum
from typing import Callable, Dict, List, Optional

import requests

logger = logging.getLogger(__name__)


def _accepts_args(cb: Callable) -> bool:
    """Return True if *cb* accepts at least one positional argument."""
    try:
        sig = inspect.signature(cb)
        params = [
            p for p in sig.parameters.values()
            if p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD, p.VAR_POSITIONAL)
        ]
        return len(params) > 0
    except (ValueError, TypeError):
        return True  # assume it accepts args if introspection fails


# ── Registry constants ─────────────────────────────────────────────────────────

SHARED_OWNER = "shared"
ACTIVE_ALIAS = "active"
VERSION_CHANGED_CHANNEL = "deltafi:artifact:version-changed"
_PLUGIN_PREFIX = "plugins/"


# ── Public enums and data classes ─────────────────────────────────────────────

class ArtifactScope(Enum):
    """Controls which owner namespace(s) are tried when resolving an artifact."""
    PLUGIN_FIRST = "PLUGIN_FIRST"   # try plugin namespace, fall back to shared
    PLUGIN_ONLY  = "PLUGIN_ONLY"    # plugin namespace only
    SHARED_ONLY  = "SHARED_ONLY"    # shared namespace only


@dataclass
class ArtifactManifest:
    """Client-side metadata for a resolved artifact version."""
    version: Optional[str]
    description: Optional[str]
    content_type: Optional[str]
    sha256: Optional[str]
    labels: Optional[Dict[str, str]]

    def is_default(self) -> bool:
        """True if published with the ``default=true`` label (seeded as plugin default)."""
        return self.labels is not None and self.labels.get("default") == "true"


@dataclass
class FetchedArtifact:
    """Result of a single artifact download from the registry."""
    manifest: ArtifactManifest
    source_prefix: str   # "shared" or "plugins/<owner>"
    data: bytes


class ArtifactStream:
    """
    A streaming download from the artifact registry.  Use as a context manager
    to ensure the underlying HTTP connection is closed when done::

        with handle.stream() as s:
            model = load_onnx(s.read())

    Unlike :meth:`ArtifactHandle.get_bytes`, streaming does not buffer the
    entire artifact in memory — the response body is consumed incrementally.
    Each call to :meth:`stream` opens a new HTTP connection and bypasses the
    shared cache.

    :attr manifest: metadata for the streamed artifact version.
    :attr source_prefix: ``"shared"`` or ``"plugins/{owner}"``.
    """

    def __init__(
        self,
        manifest: ArtifactManifest,
        source_prefix: str,
        response,  # requests.Response opened with stream=True
    ):
        self.manifest = manifest
        self.source_prefix = source_prefix
        self._response = response

    def read(self, size: int = -1) -> bytes:
        """Read *size* bytes from the stream (``-1`` reads all remaining bytes)."""
        return self._response.raw.read(size)

    def iter_content(self, chunk_size: int = 8192):
        """Iterate over the response body in chunks of *chunk_size* bytes."""
        return self._response.iter_content(chunk_size=chunk_size)

    def close(self):
        """Close the underlying HTTP connection."""
        self._response.close()

    def __enter__(self) -> "ArtifactStream":
        return self

    def __exit__(self, *args):
        self.close()


@dataclass
class VersionChangedEvent:
    """Published to the Valkey channel when an artifact alias is updated."""
    prefix: str             # "shared" or "plugins/<name>"
    artifact_name: str
    alias: str
    previous_version: Optional[str]
    new_version: str

    def is_shared(self) -> bool:
        return self.prefix == SHARED_OWNER

    @staticmethod
    def from_dict(d: dict) -> "VersionChangedEvent":
        return VersionChangedEvent(
            prefix=d.get("prefix", ""),
            artifact_name=d.get("artifactName", ""),
            alias=d.get("alias", ""),
            previous_version=d.get("previousVersion"),
            new_version=d.get("newVersion", ""),
        )


# ── Descriptor (class-level descriptor replacing @PluginArtifact) ──────────────

class Artifact:
    """
    Class-level descriptor for injecting artifact handles into action instances.

    Declare at class level; the :class:`Plugin` binds the handle at startup::

        class MyAction(TransformAction):
            model = Artifact("my-model", scope=ArtifactScope.PLUGIN_FIRST)

    After binding, ``self.model`` returns an :class:`ArtifactHandle`.
    """

    def __init__(
        self,
        artifact_name: str,
        *,
        scope: ArtifactScope = ArtifactScope.PLUGIN_FIRST,
        alias: str = ACTIVE_ALIAS,
        cacheable: bool = False,
        preload: bool = True,
        default_resource: Optional[str] = None,
    ):
        self.artifact_name = artifact_name
        self.scope = scope
        self.alias = alias
        self.cacheable = cacheable
        self.preload = preload
        self.default_resource = default_resource
        self._attr_name: Optional[str] = None  # populated by __set_name__

    def __set_name__(self, owner, name: str):
        self._attr_name = name

    def __get__(self, obj, objtype=None):
        if obj is None:
            return self  # class-level access returns the descriptor itself
        handle = obj.__dict__.get(self._attr_name)
        if handle is None:
            raise RuntimeError(
                f"Artifact '{self.artifact_name}' not bound. "
                f"Was the action created via Plugin?"
            )
        return handle

    # No __set__ → non-data descriptor → instance __dict__ takes priority after bind.

    def seed_on_missing(self) -> bool:
        """True when a default resource path is configured for seeding."""
        return self.default_resource is not None and self.default_resource.strip() != ""


# ── ArtifactHandle ─────────────────────────────────────────────────────────────

class ArtifactHandle:
    """
    Runtime handle to a resolved artifact.  All reads delegate to the
    :class:`ArtifactCacheManager` so multiple action instances sharing the same
    artifact see a single in-memory copy.

    Non-cacheable handles (``cacheable=False``, the default) always download fresh
    bytes on every :meth:`get_bytes` call, updating the shared cache as a side-effect.
    Cacheable handles (``cacheable=True``) serve bytes from the in-process cache,
    re-fetching only when a version-change notification arrives or the background
    poll detects drift.
    """

    def __init__(
        self,
        manager: "ArtifactCacheManager",
        owner: str,
        artifact_name: str,
        alias: str,
        cacheable: bool,
    ):
        self._manager = manager
        self._owner = owner
        self._artifact_name = artifact_name
        self._alias = alias
        self._cacheable = cacheable

    # ── Identity ──────────────────────────────────────────────────────────────

    def get_artifact_name(self) -> str:
        return self._artifact_name

    # ── Bytes ─────────────────────────────────────────────────────────────────

    def get_bytes(self) -> bytes:
        """Return artifact bytes.  Cacheable handles use the in-memory copy;
        non-cacheable handles re-download on every call."""
        if self._cacheable:
            return self._manager.get_bytes(self._owner, self._artifact_name, self._alias)
        return self._manager.get_fresh_bytes(self._owner, self._artifact_name, self._alias)

    def get_input_stream(self) -> "io.BytesIO":
        """Return artifact content as a :class:`io.BytesIO` stream."""
        return io.BytesIO(self.get_bytes())

    def get_string(self, encoding: str = "utf-8") -> str:
        """Decode artifact bytes using *encoding* (default UTF-8)."""
        return self.get_bytes().decode(encoding)

    # ── Manifest / metadata ───────────────────────────────────────────────────

    def _current(self) -> Optional[FetchedArtifact]:
        return self._manager.get_current(self._owner, self._artifact_name, self._alias)

    def get_manifest(self) -> ArtifactManifest:
        current = self._current()
        return current.manifest if current else ArtifactManifest(None, None, None, None, None)

    def get_version(self) -> Optional[str]:
        current = self._current()
        return current.manifest.version if current else None

    def get_description(self) -> Optional[str]:
        current = self._current()
        return current.manifest.description if current else None

    def get_content_type(self) -> Optional[str]:
        current = self._current()
        return current.manifest.content_type if current else None

    def get_labels(self) -> Optional[Dict[str, str]]:
        current = self._current()
        return current.manifest.labels if current else None

    def get_sha256(self) -> Optional[str]:
        current = self._current()
        return current.manifest.sha256 if current else None

    def get_source_prefix(self) -> Optional[str]:
        current = self._current()
        return current.source_prefix if current else None

    # ── Status ────────────────────────────────────────────────────────────────

    def is_ready(self) -> bool:
        return True

    def is_default(self) -> bool:
        current = self._manager.get_current(self._owner, self._artifact_name, self._alias)
        return current is not None and current.manifest.is_default()

    def is_shared(self) -> bool:
        return self.get_source_prefix() == "shared"

    # ── Stream ────────────────────────────────────────────────────────────────

    def stream(self) -> "ArtifactStream":
        """Open a streaming download.  Must be used as a context manager.

        Each call opens a new HTTP connection and bypasses the shared cache.
        Use this for large artifacts where buffering the full content in memory
        is undesirable.  For smaller artifacts use :meth:`get_bytes` instead.
        """
        return self._manager.stream(self._owner, self._artifact_name, self._alias)

    # ── Refresh ───────────────────────────────────────────────────────────────

    def refresh(self):
        """Force a re-download and update the shared cache entry."""
        self._manager.refresh(self._owner, self._artifact_name, self._alias)

    # ── Update callbacks ──────────────────────────────────────────────────────

    def on_update(self, callback: Callable) -> "ArtifactHandle":
        """
        Register a callback fired on version change.

        The callback is invoked after the cache has been updated, so
        ``get_bytes()`` inside the callback returns the new version's bytes.
        It may accept a :class:`VersionChangedEvent` argument, or take no
        arguments at all::

            handle.on_update(lambda: reload_model())
            handle.on_update(lambda event: print(event.new_version))
        """
        self._manager.register_callback(self._owner, self._artifact_name, self._alias, callback)
        return self


class _LazyArtifactHandle:
    """
    Wraps an unresolved :class:`Artifact` descriptor.  Resolution (and any
    startup download) is deferred until the first call that actually needs data.

    ``on_update`` callbacks registered before resolution are buffered and
    forwarded to the real handle once it is resolved.

    Created by :class:`~deltafi.plugin.Plugin` when ``Artifact(preload=False)``
    is declared on an action class.
    """

    def __init__(self, descriptor: "Artifact", manager: "ArtifactCacheManager"):
        self._descriptor = descriptor
        self._manager = manager
        self._delegate: Optional[ArtifactHandle] = None
        self._lock = threading.Lock()
        self._pending_callbacks: List[Callable] = []

    # ── Lazy resolution ───────────────────────────────────────────────────────

    def _get_delegate(self) -> ArtifactHandle:
        if self._delegate is None:
            with self._lock:
                if self._delegate is None:
                    handle = self._manager.resolve(self._descriptor)
                    for cb in self._pending_callbacks:
                        handle.on_update(cb)
                    self._pending_callbacks.clear()
                    self._delegate = handle
        return self._delegate

    # ── ArtifactHandle interface ───────────────────────────────────────────────

    def get_artifact_name(self) -> str:
        return self._descriptor.artifact_name

    def get_bytes(self) -> bytes:
        return self._get_delegate().get_bytes()

    def get_input_stream(self) -> "io.BytesIO":
        return self._get_delegate().get_input_stream()

    def get_string(self, encoding: str = "utf-8") -> str:
        return self._get_delegate().get_string(encoding)

    def stream(self) -> "ArtifactStream":
        return self._get_delegate().stream()

    def get_manifest(self) -> ArtifactManifest:
        return self._delegate.get_manifest() if self._delegate else ArtifactManifest(None, None, None, None, None)

    def get_version(self) -> Optional[str]:
        return self._delegate.get_version() if self._delegate else None

    def get_description(self) -> Optional[str]:
        return self._delegate.get_description() if self._delegate else None

    def get_content_type(self) -> Optional[str]:
        return self._delegate.get_content_type() if self._delegate else None

    def get_labels(self) -> Optional[Dict[str, str]]:
        return self._delegate.get_labels() if self._delegate else None

    def get_sha256(self) -> Optional[str]:
        return self._delegate.get_sha256() if self._delegate else None

    def get_source_prefix(self) -> Optional[str]:
        return self._delegate.get_source_prefix() if self._delegate else None

    def is_ready(self) -> bool:
        return self._delegate is not None and self._delegate.is_ready()

    def is_default(self) -> bool:
        return self._delegate.is_default() if self._delegate else False

    def is_shared(self) -> bool:
        return self._delegate.is_shared() if self._delegate else False

    def refresh(self):
        if self._delegate:
            self._delegate.refresh()

    def on_update(self, callback: Callable) -> "_LazyArtifactHandle":
        if self._delegate is not None:
            self._delegate.on_update(callback)
        else:
            with self._lock:
                if self._delegate is not None:
                    self._delegate.on_update(callback)
                else:
                    self._pending_callbacks.append(callback)
        return self


# ── Internal cache primitives ─────────────────────────────────────────────────

@dataclass(frozen=True)
class _CacheKey:
    owner: str
    artifact_name: str
    alias: str


class _CacheEntry:
    """
    A single cache slot.  Data updates are lock-protected; ``effective_owner``
    and ``backed_by_shared`` are volatile-like (written under GIL, safe for
    simple attribute reads).
    """

    def __init__(
        self,
        artifact: FetchedArtifact,
        pinned: bool,
        effective_owner: str,
        backed_by_shared: bool,
    ):
        self._lock = threading.Lock()
        self._artifact = artifact
        self._fetched_at = time.monotonic()
        self.pinned = pinned
        self.effective_owner = effective_owner
        self.backed_by_shared = backed_by_shared
        # Tracks the version last seen via a push notification or poll event.
        # NOT updated by get_fresh_bytes so the poll can still detect drift even
        # when non-cacheable handles continuously keep the cache entry current.
        self.last_notified_version: Optional[str] = artifact.manifest.version

    def get_artifact(self) -> FetchedArtifact:
        return self._artifact

    def last_fetched_at(self) -> float:
        return self._fetched_at

    def byte_count(self) -> int:
        return len(self._artifact.data)

    def update(self, fresh: FetchedArtifact):
        """Update artifact bytes only (used by get_fresh_bytes and explicit refresh)."""
        with self._lock:
            self._artifact = fresh
            self._fetched_at = time.monotonic()

    def notify_update(self, fresh: FetchedArtifact):
        """Update artifact bytes AND advance last_notified_version.
        Called by push (refresh_if_cached) and poll paths."""
        with self._lock:
            self._artifact = fresh
            self._fetched_at = time.monotonic()
            self.last_notified_version = fresh.manifest.version


# ── ArtifactRegistryClient ────────────────────────────────────────────────────

class ArtifactRegistryClient:
    """
    REST client for the DeltaFi artifact registry.

    *base_url* is the core URL (including ``/api/v2`` if applicable).
    """

    _ARTIFACTS_PATH = "/artifacts/"
    _DEFAULT_HEADERS = {
        "X-User-Permissions": "Admin",
        "X-User-Name": "deltafi-python-action-kit",
    }

    def __init__(self, base_url: str):
        self._base_url = (base_url or "").rstrip("/")

    def download(
        self, owner: str, artifact_name: str, alias: str
    ) -> Optional[FetchedArtifact]:
        """
        Download artifact bytes plus manifest headers.
        Returns ``None`` on HTTP 404; raises on other errors.
        """
        url = f"{self._base_url}{self._ARTIFACTS_PATH}{owner}/{artifact_name}/versions/{alias}"
        try:
            resp = requests.get(url, headers=self._DEFAULT_HEADERS, timeout=30)
            if resp.status_code == 404:
                return None
            resp.raise_for_status()
            manifest = self._parse_manifest_headers(resp, owner, artifact_name)
            source_prefix = resp.headers.get(
                "X-Artifact-Source-Prefix",
                "shared" if owner == SHARED_OWNER else f"plugins/{owner}",
            )
            return FetchedArtifact(manifest, source_prefix, resp.content)
        except requests.HTTPError:
            raise
        except Exception as e:
            raise RuntimeError(
                f"Failed to download artifact {owner}/{artifact_name}/{alias}"
            ) from e

    def stream(
        self, owner: str, artifact_name: str, alias: str
    ) -> "ArtifactStream":
        """
        Open a streaming download.  Returns an :class:`ArtifactStream` that
        **must** be closed when done (use as a context manager).  The underlying
        HTTP connection stays open until :meth:`ArtifactStream.close` is called.

        Raises :class:`FileNotFoundError` on HTTP 404 and
        :class:`requests.HTTPError` on other HTTP errors.
        """
        url = f"{self._base_url}{self._ARTIFACTS_PATH}{owner}/{artifact_name}/versions/{alias}"
        try:
            resp = requests.get(url, headers=self._DEFAULT_HEADERS, timeout=30, stream=True)
            if resp.status_code == 404:
                raise FileNotFoundError(
                    f"Artifact not found: {owner}/{artifact_name}/{alias}"
                )
            resp.raise_for_status()
            manifest = self._parse_manifest_headers(resp, owner, artifact_name)
            source_prefix = resp.headers.get(
                "X-Artifact-Source-Prefix",
                "shared" if owner == SHARED_OWNER else f"plugins/{owner}",
            )
            return ArtifactStream(manifest, source_prefix, resp)
        except (FileNotFoundError, requests.HTTPError):
            raise
        except Exception as e:
            raise RuntimeError(
                f"Failed to stream artifact {owner}/{artifact_name}/{alias}"
            ) from e

    def list_artifacts(self, owner: str) -> Optional[dict]:
        """
        List all artifacts for *owner*, including alias→version maps.
        Returns ``None`` on HTTP 404.
        """
        url = f"{self._base_url}{self._ARTIFACTS_PATH}{owner}"
        try:
            resp = requests.get(url, headers=self._DEFAULT_HEADERS, timeout=30)
            if resp.status_code == 404:
                return None
            resp.raise_for_status()
            return resp.json()
        except requests.HTTPError:
            raise
        except Exception as e:
            raise RuntimeError(f"Failed to list artifacts for owner '{owner}'") from e

    def seed_if_missing(
        self, owner: str, artifact_name: str, alias: str, data: bytes
    ) -> Optional[ArtifactManifest]:
        """
        Publish *data* as a new artifact version if none exists yet (HTTP 409 is ignored).
        Returns the :class:`ArtifactManifest` from the server response on success,
        or ``None`` on failure / conflict.
        """
        url = f"{self._base_url}{self._ARTIFACTS_PATH}{owner}/{artifact_name}"
        boundary = uuid.uuid4().hex
        body = self._build_multipart_body(boundary, artifact_name, alias, data)
        headers = {
            **self._DEFAULT_HEADERS,
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        }
        try:
            resp = requests.post(url, headers=headers, data=body, timeout=30)
            if resp.status_code == 409:
                return None  # already seeded by another instance
            if not resp.ok:
                logger.warning(
                    "seed_if_missing failed for %s/%s: HTTP %s",
                    owner, artifact_name, resp.status_code,
                )
                return None
            body_json = resp.json()
            return ArtifactManifest(
                version=body_json.get("version"),
                description=None,
                content_type="application/octet-stream",
                sha256=body_json.get("sha256"),
                labels={"default": "true"},
            )
        except Exception as e:
            logger.warning("seed_if_missing failed for %s/%s: %s", owner, artifact_name, e)
            return None

    # ── Internal helpers ──────────────────────────────────────────────────────

    def _parse_manifest_headers(self, resp, owner: str, artifact_name: str) -> ArtifactManifest:
        version = resp.headers.get("X-Artifact-Version")
        sha256 = resp.headers.get("X-Artifact-Sha256")
        description = resp.headers.get("X-Artifact-Description")
        content_type = resp.headers.get("Content-Type")
        if content_type and ";" in content_type:
            content_type = content_type[: content_type.index(";")].strip()
        labels_json = resp.headers.get("X-Artifact-Labels")
        labels: Optional[Dict[str, str]] = None
        if labels_json:
            try:
                labels = json.loads(labels_json)
            except Exception:
                logger.warning(
                    "Failed to parse X-Artifact-Labels for %s/%s", owner, artifact_name
                )
        return ArtifactManifest(version, description, content_type, sha256, labels)

    @staticmethod
    def _build_multipart_body(
        boundary: str, artifact_name: str, alias: str, data: bytes
    ) -> bytes:
        CRLF = b"\r\n"
        parts = b""

        def field(name: str, value: str) -> bytes:
            return (
                f"--{boundary}\r\n"
                f'Content-Disposition: form-data; name="{name}"\r\n\r\n'
                f"{value}"
            ).encode() + CRLF

        parts += field("makeActive", "true")
        parts += field("default", "true")  # label: default=true (seeded default)
        if alias != ACTIVE_ALIAS:
            parts += field("alias", alias)

        # file part
        parts += (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; filename="{artifact_name}"\r\n'
            f"Content-Type: application/octet-stream\r\n\r\n"
        ).encode()
        parts += data + CRLF
        parts += f"--{boundary}--\r\n".encode()
        return parts


# ── ArtifactCacheManager ──────────────────────────────────────────────────────

class ArtifactCacheManager:
    """
    Centralised cache for resolved artifact bytes, keyed by (owner, artifactName, alias).

    Multiple descriptors that resolve the same artifact share one :class:`_CacheEntry`,
    avoiding duplicate in-memory copies of the blob.

    Cache entries stay current through two mechanisms:

    * **Push** — version-change Valkey notifications call :meth:`refresh_if_cached`.
    * **Poll** — a background thread periodically calls :meth:`poll_for_changes` to
      catch notifications missed due to transient failures.
    """

    def __init__(self, client: ArtifactRegistryClient, owner: str):
        self._client = client
        self._owner = owner  # plugin-scoped owner (group.artifactId)
        self._cache: Dict[_CacheKey, _CacheEntry] = {}
        self._cache_lock = threading.Lock()
        self._callbacks: Dict[_CacheKey, List[Callable]] = {}
        self._poller_thread: Optional[threading.Thread] = None
        self._running = False

    # ── Resolve ───────────────────────────────────────────────────────────────

    def resolve(self, descriptor: Artifact) -> "ArtifactHandle":
        """
        Resolve *descriptor* and return an :class:`ArtifactHandle`.

        Owners are tried in scope order (plugin → shared for PLUGIN_FIRST).
        If the artifact is already cached the entry is refreshed with the latest data.
        PLUGIN_FIRST entries are always keyed by the plugin owner even when they fell
        back to the shared namespace.

        Raises :class:`RuntimeError` if the artifact is not found and no
        *default_resource* is configured.
        """
        plugin_name = self._owner
        for owner in self._owners_for(descriptor):
            fetched = self._reuse_or_download(owner, descriptor.artifact_name, descriptor.alias)
            if fetched is not None:
                key_owner = (
                    plugin_name
                    if descriptor.scope == ArtifactScope.PLUGIN_FIRST
                    else owner
                )
                backed_by_shared = (
                    descriptor.scope == ArtifactScope.PLUGIN_FIRST
                    and owner == SHARED_OWNER
                )
                key = _CacheKey(key_owner, descriptor.artifact_name, descriptor.alias)
                pinned = descriptor.alias == fetched.manifest.version

                with self._cache_lock:
                    existing = self._cache.get(key)
                    if existing is None:
                        self._cache[key] = _CacheEntry(fetched, pinned, owner, backed_by_shared)
                    else:
                        existing.update(fetched)
                        existing.effective_owner = owner
                        existing.backed_by_shared = backed_by_shared

                return ArtifactHandle(
                    self, key_owner, descriptor.artifact_name, descriptor.alias, descriptor.cacheable
                )

        return self._handle_missing(descriptor)

    # ── Bytes access ──────────────────────────────────────────────────────────

    def get_bytes(self, owner: str, artifact_name: str, alias: str) -> bytes:
        """Return cached bytes without re-downloading (for cacheable handles)."""
        entry = self._cache.get(_CacheKey(owner, artifact_name, alias))
        return entry.get_artifact().data if entry else b""

    def get_fresh_bytes(self, owner: str, artifact_name: str, alias: str) -> bytes:
        """Always download fresh bytes; update the shared cache as a side-effect."""
        entry = self._cache.get(_CacheKey(owner, artifact_name, alias))
        download_owner = entry.effective_owner if entry else owner
        fresh = self._client.download(download_owner, artifact_name, alias)
        if fresh is None:
            if entry:
                logger.warning(
                    "Artifact %s/%s/%s is no longer available — returning cached bytes (v%s)",
                    download_owner, artifact_name, alias, entry.get_artifact().manifest.version,
                )
                return entry.get_artifact().data
            return b""
        if entry:
            entry.update(fresh)
        return fresh.data

    def get_current(self, owner: str, artifact_name: str, alias: str) -> Optional[FetchedArtifact]:
        """Return the current cached :class:`FetchedArtifact` for metadata access."""
        entry = self._cache.get(_CacheKey(owner, artifact_name, alias))
        return entry.get_artifact() if entry else None

    # ── Stream ────────────────────────────────────────────────────────────────

    def stream(self, owner: str, artifact_name: str, alias: str) -> "ArtifactStream":
        """Open a streaming download, bypassing the cache.  Use as a context manager."""
        entry = self._cache.get(_CacheKey(owner, artifact_name, alias))
        download_owner = entry.effective_owner if entry else owner
        return self._client.stream(download_owner, artifact_name, alias)

    # ── Refresh ───────────────────────────────────────────────────────────────

    def refresh(self, owner: str, artifact_name: str, alias: str):
        """Force re-download of a cached entry (called by :meth:`ArtifactHandle.refresh`)."""
        entry = self._cache.get(_CacheKey(owner, artifact_name, alias))
        if entry is None:
            return
        fresh = self._client.download(entry.effective_owner, artifact_name, alias)
        if fresh is not None:
            entry.update(fresh)
        else:
            logger.warning(
                "Artifact %s/%s/%s no longer available — refresh had no effect (retaining v%s)",
                entry.effective_owner, artifact_name, alias, entry.get_artifact().manifest.version,
            )

    def refresh_if_cached(self, owner: str, artifact_name: str, alias: str):
        """
        Re-download and update only if the entry is already cached.
        Called by the notification listener on version-change events.

        Shared-owner path: updates ``("shared", …)`` entry then propagates to
        any PLUGIN_FIRST entries that are ``backed_by_shared``.

        Plugin-owner path: downloads from the plugin namespace; promotes
        PLUGIN_FIRST entries that were ``backed_by_shared``.
        """
        entry = self._cache.get(_CacheKey(owner, artifact_name, alias))
        fresh: Optional[FetchedArtifact] = None
        if entry is not None:
            fresh = self._client.download(owner, artifact_name, alias)
            if fresh is not None:
                if entry.backed_by_shared:
                    logger.info(
                        "Artifact '%s/%s' (alias '%s'): PLUGIN_FIRST entry promoted from "
                        "shared (v%s) to plugin-specific (v%s)",
                        owner, artifact_name, alias,
                        entry.get_artifact().manifest.version, fresh.manifest.version,
                    )
                entry.notify_update(fresh)
                entry.effective_owner = owner
                entry.backed_by_shared = False
            else:
                logger.warning(
                    "Artifact %s/%s/%s no longer available — retaining cached version %s",
                    owner, artifact_name, alias, entry.get_artifact().manifest.version,
                )
        if owner == SHARED_OWNER:
            self._propagate_shared_update(artifact_name, alias, fresh)

    # ── Callbacks ─────────────────────────────────────────────────────────────

    def register_callback(
        self, owner: str, artifact_name: str, alias: str, callback: Callable
    ):
        """Register *callback* to be fired on version-change for this entry."""
        key = _CacheKey(owner, artifact_name, alias)
        self._callbacks.setdefault(key, []).append(callback)

    def invoke_callbacks(
        self, owner: str, artifact_name: str, alias: str, event: "dict | VersionChangedEvent"
    ):
        """
        Invoke all callbacks registered for ``(owner, artifact_name, alias)``.
        *event* is normalised to a :class:`VersionChangedEvent` before being passed.
        Callbacks that accept zero parameters are called without the event.
        Each callback runs on the caller's
        thread; failures are logged and do not prevent subsequent callbacks from running.
        """
        key = _CacheKey(owner, artifact_name, alias)
        cbs = list(self._callbacks.get(key, []))
        if not cbs:
            return
        if isinstance(event, dict):
            event = VersionChangedEvent.from_dict(event)
        for cb in cbs:
            try:
                if _accepts_args(cb):
                    cb(event)
                else:
                    cb()
            except Exception as e:
                logger.warning(
                    "Artifact update callback failed for '%s/%s/%s': %s",
                    owner, artifact_name, alias, e,
                )

    # ── Startup callbacks ─────────────────────────────────────────────────────

    def fire_startup_callbacks(self):
        """
        Fire all registered callbacks with a synthetic event for each cached entry.

        Called after all actions are created and their ``on_update`` callbacks
        are registered.  Each callback receives a :class:`VersionChangedEvent`
        with ``previous_version=None``, mirroring the Go/C++ pattern where
        initial resolution is treated as a version change.
        """
        for key, cbs in list(self._callbacks.items()):
            if not cbs:
                continue
            entry = self._cache.get(key)
            current_version = (
                entry.get_artifact().manifest.version if entry else None
            )
            source_prefix = (
                SHARED_OWNER if key.owner == SHARED_OWNER
                else f"{_PLUGIN_PREFIX}{key.owner}"
            )
            event = VersionChangedEvent(
                prefix=source_prefix,
                artifact_name=key.artifact_name,
                alias=key.alias,
                previous_version=None,
                new_version=current_version or "",
            )
            self.invoke_callbacks(key.owner, key.artifact_name, key.alias, event)

    # ── Background poll ───────────────────────────────────────────────────────

    def poll_for_changes(self):
        """
        Compare each non-pinned cached entry against the registry's current alias
        mapping and re-fetch any that have drifted.  Fires callbacks after refreshing.

        Package-visible for tests; do not call directly in production (use
        :meth:`start_poller`).
        """
        if not self._cache:
            return
        by_owner = self._group_entries_by_owner()
        for owner, keys in by_owner.items():
            try:
                self._poll_for_owner(owner, keys)
            except Exception as e:
                logger.warning("Artifact cache poll failed for owner '%s': %s", owner, e)

    def start_poller(self, interval_s: int = 60):
        """Start the background polling daemon thread."""
        self._running = True
        t = threading.Thread(
            target=self._poll_loop,
            args=(interval_s,),
            name="artifact-cache-poll",
            daemon=True,
        )
        self._poller_thread = t
        t.start()
        logger.debug("Artifact cache poll started: interval=%ds", interval_s)

    def stop(self):
        """Stop the background poller (waits up to 5 s for the thread to exit)."""
        self._running = False
        if self._poller_thread:
            self._poller_thread.join(timeout=5)

    # ── Internal helpers ──────────────────────────────────────────────────────

    def _poll_loop(self, interval_s: int):
        while self._running:
            time.sleep(interval_s)
            try:
                self.poll_for_changes()
            except Exception as e:
                logger.warning("Artifact cache poll error: %s", e)

    def _poll_for_owner(self, owner: str, keys: List[_CacheKey]):
        alias_map = self._build_alias_map(owner)
        if not alias_map:
            return
        for key in keys:
            remote_version = alias_map.get(key.artifact_name, {}).get(key.alias)
            entry = self._cache.get(key)
            if remote_version is None and entry is not None:
                logger.warning(
                    "Artifact %s/%s/%s is no longer in the registry — "
                    "continuing with cached version %s",
                    owner, key.artifact_name, key.alias,
                    entry.get_artifact().manifest.version,
                )
            elif self._version_has_diverged(remote_version, entry):
                cached_version = entry.get_artifact().manifest.version
                logger.debug(
                    "Poll detected version change for %s/%s/%s: %s → %s",
                    owner, key.artifact_name, key.alias, cached_version, remote_version,
                )
                fresh = self._client.download(owner, key.artifact_name, key.alias)
                if fresh is not None:
                    entry.notify_update(fresh)
                    source_prefix = (
                        "shared" if owner == SHARED_OWNER else f"plugins/{owner}"
                    )
                    evt = VersionChangedEvent(
                        prefix=source_prefix,
                        artifact_name=key.artifact_name,
                        alias=key.alias,
                        previous_version=cached_version,
                        new_version=remote_version,
                    )
                    self.invoke_callbacks(key.owner, key.artifact_name, key.alias, evt)

    def _group_entries_by_owner(self) -> Dict[str, List[_CacheKey]]:
        by_owner: Dict[str, List[_CacheKey]] = {}
        for key, entry in self._cache.items():
            if not entry.pinned:
                by_owner.setdefault(entry.effective_owner, []).append(key)
        return by_owner

    def _build_alias_map(self, owner: str) -> Dict[str, Dict[str, str]]:
        response = self._client.list_artifacts(owner)
        if response is None:
            return {}
        alias_map: Dict[str, Dict[str, str]] = {}
        for summary in response.get("artifacts", []):
            name = summary.get("artifactName")
            aliases = summary.get("aliases", {})
            if name:
                alias_map[name] = aliases
        return alias_map

    @staticmethod
    def _version_has_diverged(
        remote_version: Optional[str], entry: Optional[_CacheEntry]
    ) -> bool:
        if remote_version is None or entry is None:
            return False
        # Compare against last_notified_version rather than the cached artifact version
        # so that non-cacheable handles (whose get_fresh_bytes continuously updates the
        # artifact) still trigger callbacks via the poll when they miss a push notification.
        return remote_version != entry.last_notified_version

    def _reuse_or_download(
        self, owner: str, artifact_name: str, alias: str
    ) -> Optional[FetchedArtifact]:
        """
        For shared-namespace downloads only: return cached bytes rather than
        downloading again.  For all other owners the download is always performed fresh.
        """
        if owner == SHARED_OWNER:
            shared_entry = self._cache.get(_CacheKey(owner, artifact_name, alias))
            if shared_entry is not None:
                return shared_entry.get_artifact()
            for key, entry in self._cache.items():
                if (
                    entry.backed_by_shared
                    and key.artifact_name == artifact_name
                    and key.alias == alias
                ):
                    return entry.get_artifact()
        return self._client.download(owner, artifact_name, alias)

    def _propagate_shared_update(
        self,
        artifact_name: str,
        alias: str,
        already_fetched: Optional[FetchedArtifact],
    ):
        """
        Push a shared-namespace update to all PLUGIN_FIRST entries that are
        currently ``backed_by_shared`` and match ``(artifact_name, alias)``.
        """
        fresh = already_fetched
        for key, entry in self._cache.items():
            if (
                entry.backed_by_shared
                and key.artifact_name == artifact_name
                and key.alias == alias
            ):
                if fresh is None:
                    fresh = self._client.download(SHARED_OWNER, artifact_name, alias)
                    if fresh is None:
                        logger.warning(
                            "Shared artifact %s/%s no longer available — "
                            "backedByShared entries retain cached bytes",
                            artifact_name, alias,
                        )
                        return
                prev_version = entry.get_artifact().manifest.version
                entry.notify_update(fresh)
                if fresh.manifest.version != prev_version:
                    evt = VersionChangedEvent(
                        prefix="shared",
                        artifact_name=artifact_name,
                        alias=alias,
                        previous_version=prev_version,
                        new_version=fresh.manifest.version,
                    )
                    self.invoke_callbacks(key.owner, artifact_name, alias, evt)

    def _owners_for(self, descriptor: Artifact) -> List[str]:
        plugin_name = self._owner
        if descriptor.scope == ArtifactScope.PLUGIN_ONLY:
            return [plugin_name]
        if descriptor.scope == ArtifactScope.SHARED_ONLY:
            return [SHARED_OWNER]
        # PLUGIN_FIRST
        return [plugin_name, SHARED_OWNER]

    def _handle_missing(self, descriptor: Artifact) -> "ArtifactHandle":
        if not descriptor.seed_on_missing():
            raise RuntimeError(f"Artifact not found: {descriptor.artifact_name}")
        # Load default resource bytes
        data: Optional[bytes] = None
        resource_path = descriptor.default_resource
        try:
            with open(resource_path, "rb") as f:
                data = f.read()
        except Exception:
            pass
        if data is None:
            raise RuntimeError(f"Default resource not found: {resource_path}")
        manifest = self._client.seed_if_missing(
            self._owner, descriptor.artifact_name, descriptor.alias, data
        )
        if manifest is None:
            raise RuntimeError(
                f"Failed to seed default artifact: {descriptor.artifact_name}"
            )
        # Cache the local bytes with server-provided manifest so
        # get_bytes() and on_update() work immediately
        source_prefix = f"plugins/{self._owner}"
        fetched = FetchedArtifact(manifest, source_prefix, data)
        key = _CacheKey(self._owner, descriptor.artifact_name, descriptor.alias)
        with self._cache_lock:
            self._cache[key] = _CacheEntry(fetched, False, self._owner, False)
        return ArtifactHandle(
            self, self._owner, descriptor.artifact_name, descriptor.alias, descriptor.cacheable
        )


# ── ArtifactVersionChangeListener ─────────────────────────────────────────────

class ArtifactVersionChangeListener:
    """
    Subscribes to the Valkey pub/sub channel for artifact version-change events
    and invalidates matching entries in the :class:`ArtifactCacheManager`.

    A background daemon thread maintains the subscription, reconnecting
    automatically after transient disconnections.
    """

    def __init__(self, valkey_pool, cache_manager: ArtifactCacheManager):
        self._valkey_pool = valkey_pool
        self._cache_manager = cache_manager
        self._running = False
        self._thread: Optional[threading.Thread] = None

    def start(self):
        """Start the listener daemon thread."""
        self._running = True
        self._thread = threading.Thread(
            target=self._subscribe_loop,
            name="artifact-cache-listener",
            daemon=True,
        )
        self._thread.start()

    def stop(self):
        """Stop the listener thread (waits up to 5 s for exit)."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=5)

    def _subscribe_loop(self):
        import redis as redis_lib

        while self._running:
            try:
                r = redis_lib.Redis(connection_pool=self._valkey_pool)
                pubsub = r.pubsub()
                pubsub.subscribe(VERSION_CHANGED_CHANNEL)
                for message in pubsub.listen():
                    if not self._running:
                        break
                    if message["type"] == "message":
                        self._handle(message["data"])
            except Exception as e:
                if self._running:
                    logger.warning(
                        "Artifact cache listener disconnected, retrying in 5s: %s", e
                    )
                    time.sleep(5)

    def _handle(self, data):
        try:
            if isinstance(data, bytes):
                data = data.decode("utf-8")
            event_dict = json.loads(data)
            owner = self._owner_from(event_dict)
            artifact_name = event_dict.get("artifactName")
            alias = event_dict.get("alias")
            self._cache_manager.refresh_if_cached(owner, artifact_name, alias)
            self._cache_manager.invoke_callbacks(owner, artifact_name, alias, event_dict)
        except Exception as e:
            logger.warning("Failed to process version-change event: %s", e)

    @staticmethod
    def _owner_from(event: dict) -> str:
        prefix = event.get("prefix", "")
        if prefix == SHARED_OWNER:
            return SHARED_OWNER
        if prefix.startswith(_PLUGIN_PREFIX):
            return prefix[len(_PLUGIN_PREFIX):]
        return prefix
