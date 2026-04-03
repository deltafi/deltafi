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

import pytest
from unittest.mock import MagicMock

from deltafi.artifact import (
    Artifact,
    ArtifactCacheManager,
    ArtifactHandle,
    ArtifactManifest,
    ArtifactRegistryClient,
    ArtifactScope,
    ArtifactStream,
    FetchedArtifact,
    VersionChangedEvent,
    _LazyArtifactHandle,
    SHARED_OWNER,
)

# ── Fixtures ──────────────────────────────────────────────────────────────────

PLUGIN = "my-plugin"
ARTIFACT = "my-model"
VERSION = "20240101-120000"
BYTES = b"model bytes"
SOURCE_PREFIX = f"plugins/{PLUGIN}"
MANIFEST = ArtifactManifest(VERSION, "description", "application/octet-stream", "sha256abc", None)


def make_fetched(manifest=MANIFEST, source_prefix=SOURCE_PREFIX, data=BYTES):
    return FetchedArtifact(manifest, source_prefix, data)


def make_manager(client=None) -> ArtifactCacheManager:
    """Create a manager without starting the poller (tests stay single-threaded)."""
    if client is None:
        client = MagicMock(spec=ArtifactRegistryClient)
    return ArtifactCacheManager(client, PLUGIN)


def descriptor(scope=ArtifactScope.PLUGIN_ONLY, cacheable=True, default_resource=None):
    d = Artifact(ARTIFACT, scope=scope, cacheable=cacheable)
    if default_resource is not None:
        d.default_resource = default_resource
    return d


def version_event(new_version="v2"):
    return VersionChangedEvent(SOURCE_PREFIX, ARTIFACT, "active", VERSION, new_version)


# ── resolve: scope semantics ───────────────────────────────────────────────────

class TestResolveScope:

    def test_plugin_first_finds_in_plugin_namespace(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor(ArtifactScope.PLUGIN_FIRST))

        assert handle.get_bytes() == BYTES
        assert handle.get_version() == VERSION
        assert handle.is_shared() is False
        assert handle.get_source_prefix() == SOURCE_PREFIX

        # Shared namespace must not have been tried
        shared_calls = [
            call for call in client.download.call_args_list
            if call.args[0] == SHARED_OWNER
        ]
        assert shared_calls == []

    def test_plugin_first_falls_back_to_shared(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.side_effect = lambda owner, *a: (
            None if owner == PLUGIN else make_fetched(source_prefix="shared")
        )
        manager = make_manager(client)

        handle = manager.resolve(descriptor(ArtifactScope.PLUGIN_FIRST))

        assert handle.get_bytes() == BYTES
        assert handle.is_shared() is True

    def test_plugin_only_does_not_fall_back_to_shared(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = None
        manager = make_manager(client)

        with pytest.raises(RuntimeError, match=ARTIFACT):
            manager.resolve(descriptor(ArtifactScope.PLUGIN_ONLY))

        shared_calls = [c for c in client.download.call_args_list if c.args[0] == SHARED_OWNER]
        assert shared_calls == []

    def test_resolve_shared_only(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched(source_prefix="shared")
        manager = make_manager(client)

        handle = manager.resolve(descriptor(ArtifactScope.SHARED_ONLY))

        assert handle.get_bytes() == BYTES
        assert handle.is_shared() is True

        plugin_calls = [c for c in client.download.call_args_list if c.args[0] == PLUGIN]
        assert plugin_calls == []


# ── Caching behaviour ─────────────────────────────────────────────────────────

class TestCaching:

    def test_cacheable_get_bytes_does_not_re_fetch(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor(cacheable=True))
        _ = handle.get_bytes()
        _ = handle.get_bytes()

        # Only 1 download: the initial resolve
        assert client.download.call_count == 1

    def test_non_cacheable_get_bytes_always_downloads_fresh(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor(cacheable=False))
        handle.get_bytes()
        handle.get_bytes()

        # 1 (resolve) + 2 (each get_bytes) = 3
        assert client.download.call_count == 3

    def test_non_cacheable_updates_cache_for_cacheable_handle(self):
        fresh_bytes = b"fresh bytes"
        fresh_manifest = ArtifactManifest("v2", "updated", "text/plain", "sha2", None)
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.side_effect = [
            make_fetched(),                              # resolve cacheable
            make_fetched(),                              # resolve non-cacheable
            make_fetched(fresh_manifest, SOURCE_PREFIX, fresh_bytes),  # get_bytes on non-cacheable
        ]
        manager = make_manager(client)

        cacheable_handle = manager.resolve(descriptor(cacheable=True))
        uncached_handle = manager.resolve(descriptor(cacheable=False))

        from_uncached = uncached_handle.get_bytes()
        assert from_uncached == fresh_bytes
        assert cacheable_handle.get_bytes() == fresh_bytes


# ── reuse_or_download deduplication ──────────────────────────────────────────

class TestReuseOrDownload:

    def test_shared_download_reused_for_plugin_first_fallback(self):
        """
        SHARED_ONLY resolves first.  PLUGIN_FIRST falls back to shared: the already-cached
        shared entry must be returned without a second network call.
        """
        client = MagicMock(spec=ArtifactRegistryClient)
        shared_fetched = make_fetched(source_prefix="shared")

        def download(owner, name, alias):
            if owner == SHARED_OWNER:
                return shared_fetched
            return None  # plugin namespace absent

        client.download.side_effect = download
        manager = make_manager(client)

        # SHARED_ONLY resolves and populates ("shared", …) cache entry
        shared_handle = manager.resolve(descriptor(ArtifactScope.SHARED_ONLY))
        # PLUGIN_FIRST falls back; should reuse the cached shared bytes
        plugin_handle = manager.resolve(descriptor(ArtifactScope.PLUGIN_FIRST))

        assert shared_handle.get_bytes() == BYTES
        assert plugin_handle.get_bytes() == BYTES

        # Shared download: once for SHARED_ONLY resolve; plugin-namespace download once.
        # No second shared download for PLUGIN_FIRST.
        shared_calls = [c for c in client.download.call_args_list if c.args[0] == SHARED_OWNER]
        assert len(shared_calls) == 1


# ── refresh_if_cached ─────────────────────────────────────────────────────────

class TestRefreshIfCached:

    def test_promotes_backed_by_shared_entry_when_plugin_version_published(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        plugin_bytes = b"plugin bytes"
        plugin_manifest = ArtifactManifest("v2", "plugin", "application/octet-stream", "sha2", None)

        client.download.side_effect = [
            None,                                          # PLUGIN_FIRST initial: absent
            make_fetched(source_prefix="shared"),          # PLUGIN_FIRST fallback to shared
            make_fetched(plugin_manifest, SOURCE_PREFIX, plugin_bytes),  # promotion
        ]
        manager = make_manager(client)

        handle = manager.resolve(descriptor(ArtifactScope.PLUGIN_FIRST))
        assert handle.is_shared() is True
        assert handle.get_bytes() == BYTES

        manager.refresh_if_cached(PLUGIN, ARTIFACT, "active")

        assert handle.get_bytes() == plugin_bytes
        assert handle.is_shared() is False
        assert handle.get_version() == "v2"

    def test_propagates_shared_update_to_plugin_first_backed_entry(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        shared_v1 = b"shared-v1"
        shared_v2 = b"shared-v2"
        manifest_v1 = ArtifactManifest("shared-v1", "shared", "text/plain", "sha-s1", None)
        manifest_v2 = ArtifactManifest("shared-v2", "shared", "text/plain", "sha-s2", None)

        client.download.side_effect = [
            None,                                          # plugin absent
            make_fetched(manifest_v1, "shared", shared_v1),  # fallback
            make_fetched(manifest_v2, "shared", shared_v2),  # propagation download
        ]
        manager = make_manager(client)

        handle = manager.resolve(descriptor(ArtifactScope.PLUGIN_FIRST))
        assert handle.get_bytes() == shared_v1
        assert handle.is_shared() is True

        events = []
        handle.on_update(lambda evt: events.append(evt.new_version))

        # No ("shared",…) cache entry — propagateSharedUpdate downloads from shared
        manager.refresh_if_cached(SHARED_OWNER, ARTIFACT, "active")

        assert handle.get_bytes() == shared_v2
        assert events == ["shared-v2"]


# ── poll_for_changes ──────────────────────────────────────────────────────────

class TestPollForChanges:

    def test_detects_version_drift(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        new_bytes = b"new bytes"
        new_manifest = ArtifactManifest("v2", "new", "text/plain", "sha2", None)

        client.download.side_effect = [
            make_fetched(),                                   # resolve
            make_fetched(new_manifest, SOURCE_PREFIX, new_bytes),  # poll re-fetch
        ]
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        assert handle.get_version() == VERSION

        client.list_artifacts.return_value = {
            "artifacts": [{"artifactName": ARTIFACT, "aliases": {"active": "v2"}}]
        }
        manager.poll_for_changes()

        assert handle.get_version() == "v2"
        assert handle.get_bytes() == new_bytes
        assert client.download.call_count == 2

    def test_no_version_change_does_not_refetch(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        manager.resolve(descriptor())

        client.list_artifacts.return_value = {
            "artifacts": [{"artifactName": ARTIFACT, "aliases": {"active": VERSION}}]
        }
        manager.poll_for_changes()

        # Only one download: the initial resolve
        assert client.download.call_count == 1

    def test_pinned_entries_are_not_polled(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        # alias == version → pinned
        d = Artifact(ARTIFACT, alias=VERSION)
        manager.resolve(d)

        manager.poll_for_changes()

        client.list_artifacts.assert_not_called()

    def test_fires_callbacks_on_version_change(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        new_bytes = b"new bytes"
        new_manifest = ArtifactManifest("v2", "new", "text/plain", "sha2", None)

        client.download.side_effect = [
            make_fetched(),
            make_fetched(new_manifest, SOURCE_PREFIX, new_bytes),
        ]
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        versions = []
        handle.on_update(lambda evt: versions.append(evt.new_version))

        client.list_artifacts.return_value = {
            "artifacts": [{"artifactName": ARTIFACT, "aliases": {"active": "v2"}}]
        }
        manager.poll_for_changes()

        assert versions == ["v2"]


# ── Callbacks ─────────────────────────────────────────────────────────────────

class TestCallbacks:

    def test_on_update_receives_event_and_refreshed_bytes(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        new_bytes = b"new bytes"
        new_manifest = ArtifactManifest("v2", "desc", "text/plain", "sha2", None)

        client.download.side_effect = [
            make_fetched(),
            make_fetched(new_manifest, SOURCE_PREFIX, new_bytes),
        ]
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        calls = []
        handle.on_update(lambda evt: calls.append(evt.new_version))

        manager.refresh_if_cached(PLUGIN, ARTIFACT, "active")
        manager.invoke_callbacks(PLUGIN, ARTIFACT, "active", version_event("v2"))

        assert calls == ["v2"]
        assert handle.get_bytes() == new_bytes

    def test_non_cacheable_on_update_still_fired_on_push_notification(self):
        """cacheable=False handles must still fire on_update callbacks via the push path."""
        client = MagicMock(spec=ArtifactRegistryClient)
        new_bytes = b"new bytes"
        new_manifest = ArtifactManifest("v2", "desc", "text/plain", "sha2", None)

        client.download.side_effect = [
            make_fetched(),
            make_fetched(new_manifest, SOURCE_PREFIX, new_bytes),
        ]
        manager = make_manager(client)

        handle = manager.resolve(descriptor(cacheable=False))
        calls = []
        handle.on_update(lambda evt: calls.append(evt.new_version))

        manager.refresh_if_cached(PLUGIN, ARTIFACT, "active")
        manager.invoke_callbacks(PLUGIN, ARTIFACT, "active", version_event("v2"))

        assert calls == ["v2"]

    def test_non_cacheable_on_update_still_fired_by_poll(self):
        """Poll must still detect drift and fire callbacks for non-cacheable handles,
        even after get_fresh_bytes has updated the cache entry to the latest version."""
        client = MagicMock(spec=ArtifactRegistryClient)
        fresh_bytes = b"fresh"
        v2_manifest = ArtifactManifest("v2", "d", "text/plain", "sha2", None)

        client.download.side_effect = [
            make_fetched(),                                          # resolve (v1)
            make_fetched(v2_manifest, SOURCE_PREFIX, fresh_bytes),  # get_bytes() call
            make_fetched(v2_manifest, SOURCE_PREFIX, fresh_bytes),  # poll re-fetch
        ]
        manager = make_manager(client)

        handle = manager.resolve(descriptor(cacheable=False))
        versions = []
        handle.on_update(lambda evt: versions.append(evt.new_version))

        # Simulate a get_bytes() call that updates the cache entry to v2 via update()
        # (not notify_update) — last_notified_version stays at v1
        handle.get_bytes()

        # Poll sees v2 remotely; last_notified_version is still v1, so diverged = True
        client.list_artifacts.return_value = {
            "artifacts": [{"artifactName": ARTIFACT, "aliases": {"active": "v2"}}]
        }
        manager.poll_for_changes()

        assert versions == ["v2"]

    def test_callback_not_fired_for_different_owner(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        calls = []
        handle.on_update(lambda evt: calls.append("fired"))

        manager.invoke_callbacks(SHARED_OWNER, ARTIFACT, "active", version_event())
        assert calls == []

        manager.invoke_callbacks(PLUGIN, ARTIFACT, "active", version_event())
        assert calls == ["fired"]

    def test_multiple_callbacks_all_invoked(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        calls = []
        handle.on_update(lambda evt: calls.append("cb1"))
        handle.on_update(lambda evt: calls.append("cb2"))

        manager.invoke_callbacks(PLUGIN, ARTIFACT, "active", version_event())
        assert calls == ["cb1", "cb2"]

    def test_failing_callback_does_not_prevent_others(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        calls = []
        handle.on_update(lambda evt: (_ for _ in ()).throw(RuntimeError("boom")))
        handle.on_update(lambda evt: calls.append("survived"))

        manager.invoke_callbacks(PLUGIN, ARTIFACT, "active", version_event())
        assert calls == ["survived"]

    def test_invoke_callbacks_missing_key_does_not_throw(self):
        manager = make_manager()
        # Must not raise
        manager.invoke_callbacks(PLUGIN, "unknown-artifact", "active", version_event())

    def test_runnable_style_callback(self):
        """Zero-argument callable is called without the event."""
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        calls = []
        handle.on_update(lambda: calls.append("runnable"))

        manager.invoke_callbacks(PLUGIN, ARTIFACT, "active", version_event())
        assert calls == ["runnable"]

    def test_mixed_callback_arities(self):
        """Zero-arg and one-arg callbacks coexist on the same handle."""
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        calls = []
        handle.on_update(lambda: calls.append("no-arg"))
        handle.on_update(lambda evt: calls.append(f"with-arg:{evt.new_version}"))

        manager.invoke_callbacks(PLUGIN, ARTIFACT, "active", version_event("v2"))
        assert calls == ["no-arg", "with-arg:v2"]


# ── refresh() ─────────────────────────────────────────────────────────────────

class TestRefresh:

    def test_refresh_updates_shared_cache_for_all_handles(self):
        new_bytes = b"updated bytes"
        new_manifest = ArtifactManifest("v2", "new desc", "text/plain", "newsha", None)
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.side_effect = [
            make_fetched(),   # resolve d1
            make_fetched(),   # resolve d2
            make_fetched(new_manifest, SOURCE_PREFIX, new_bytes),  # refresh
        ]
        manager = make_manager(client)

        h1 = manager.resolve(descriptor())
        h2 = manager.resolve(descriptor())

        assert h1.get_version() == VERSION
        assert h2.get_version() == VERSION

        h1.refresh()

        assert h1.get_version() == "v2"
        assert h2.get_version() == "v2"
        assert h1.get_bytes() == new_bytes
        assert h2.get_bytes() == new_bytes


# ── Pinned entries ────────────────────────────────────────────────────────────

class TestPinned:

    def test_concrete_version_alias_is_pinned_and_not_polled(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        d = Artifact(ARTIFACT, alias=VERSION, cacheable=True)  # alias == manifest version → pinned
        handle = manager.resolve(d)

        assert handle.get_bytes() == BYTES
        handle.get_bytes()
        handle.get_bytes()
        assert client.download.call_count == 1  # no extra fetches

        manager.poll_for_changes()
        client.list_artifacts.assert_not_called()


# ── PLUGIN_FIRST coexistence with SHARED_ONLY ─────────────────────────────────

class TestCoexistence:

    def test_shared_update_propagates_to_plugin_first_backed_entries(self):
        """
        PLUGIN_FIRST falls back to shared; a shared-namespace update must propagate
        the new bytes to the plugin-keyed entry and fire callbacks there.
        """
        client = MagicMock(spec=ArtifactRegistryClient)
        shared_v1 = b"shared-v1"
        shared_v2 = b"shared-v2"
        m_v1 = ArtifactManifest("shared-v1", "s", "text/plain", "sha1", None)
        m_v2 = ArtifactManifest("shared-v2", "s", "text/plain", "sha2", None)

        client.download.side_effect = [
            None,                                               # plugin absent
            make_fetched(m_v1, "shared", shared_v1),           # fallback
            make_fetched(m_v2, "shared", shared_v2),           # propagation download
        ]
        manager = make_manager(client)

        handle = manager.resolve(descriptor(ArtifactScope.PLUGIN_FIRST))
        assert handle.get_bytes() == shared_v1

        versions = []
        handle.on_update(lambda evt: versions.append(evt.new_version))

        manager.refresh_if_cached(SHARED_OWNER, ARTIFACT, "active")

        assert handle.get_bytes() == shared_v2
        assert versions == ["shared-v2"]


# ── Descriptor / Artifact class ───────────────────────────────────────────────

class TestArtifactDescriptor:

    def test_descriptor_raises_if_not_bound(self):
        class FakeAction:
            model = Artifact("my-model")

        action = FakeAction.__new__(FakeAction)
        with pytest.raises(RuntimeError, match="not bound"):
            _ = action.model

    def test_descriptor_class_access_returns_self(self):
        class FakeAction:
            model = Artifact("my-model")

        assert isinstance(FakeAction.model, Artifact)

    def test_descriptor_bound_via_instance_dict(self):
        class FakeAction:
            model = Artifact("my-model")

        action = FakeAction.__new__(FakeAction)
        sentinel = object()
        action.__dict__["model"] = sentinel
        assert action.model is sentinel


# ── VersionChangedEvent helpers ───────────────────────────────────────────────

class TestVersionChangedEvent:

    def test_from_dict(self):
        d = {
            "prefix": "plugins/my-plugin",
            "artifactName": "my-model",
            "alias": "active",
            "previousVersion": "v1",
            "newVersion": "v2",
        }
        evt = VersionChangedEvent.from_dict(d)
        assert evt.artifact_name == "my-model"
        assert evt.new_version == "v2"
        assert evt.is_shared() is False

    def test_is_shared(self):
        evt = VersionChangedEvent("shared", "a", "active", None, "v1")
        assert evt.is_shared() is True


# ── ArtifactStream ────────────────────────────────────────────────────────────

class TestArtifactStream:

    def _mock_response(self, data=b"stream bytes"):
        resp = MagicMock()
        resp.raw.read.return_value = data
        resp.iter_content.return_value = iter([data])
        return resp

    def test_read_delegates_to_raw(self):
        resp = self._mock_response(b"hello")
        s = ArtifactStream(MANIFEST, SOURCE_PREFIX, resp)
        assert s.read() == b"hello"
        resp.raw.read.assert_called_once_with(-1)

    def test_iter_content_delegates(self):
        resp = self._mock_response(b"chunk")
        s = ArtifactStream(MANIFEST, SOURCE_PREFIX, resp)
        chunks = list(s.iter_content(chunk_size=4096))
        assert chunks == [b"chunk"]

    def test_context_manager_closes_response(self):
        resp = self._mock_response()
        with ArtifactStream(MANIFEST, SOURCE_PREFIX, resp):
            pass
        resp.close.assert_called_once()

    def test_client_stream_uses_stream_true(self):
        import unittest.mock as mock
        with mock.patch("deltafi.artifact.requests.get") as mock_get:
            resp = self._mock_response(b"data")
            resp.status_code = 200
            resp.headers = {
                "X-Artifact-Version": VERSION,
                "X-Artifact-Sha256": "abc",
                "Content-Type": "application/octet-stream",
            }
            mock_get.return_value = resp
            client = ArtifactRegistryClient("http://localhost:8042")
            s = client.stream(PLUGIN, ARTIFACT, "active")
            assert isinstance(s, ArtifactStream)
            mock_get.assert_called_once()
            _, kwargs = mock_get.call_args
            assert kwargs.get("stream") is True
            s.close()

    def test_client_stream_raises_on_404(self):
        import unittest.mock as mock
        with mock.patch("deltafi.artifact.requests.get") as mock_get:
            resp = MagicMock()
            resp.status_code = 404
            mock_get.return_value = resp
            client = ArtifactRegistryClient("http://localhost:8042")
            with pytest.raises(FileNotFoundError):
                client.stream(PLUGIN, ARTIFACT, "active")

    def test_handle_stream_uses_effective_owner(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        mock_stream = MagicMock(spec=ArtifactStream)
        client.stream.return_value = mock_stream
        manager = make_manager(client)
        fetched = make_fetched()
        d = descriptor()
        client.download.return_value = fetched
        manager.resolve(d)
        # Override effective_owner to shared to verify it's used
        from deltafi.artifact import _CacheKey
        entry = manager._cache[_CacheKey(PLUGIN, ARTIFACT, "active")]
        entry.effective_owner = "other-owner"
        handle = ArtifactHandle(manager, PLUGIN, ARTIFACT, "active", False)
        result = handle.stream()
        client.stream.assert_called_once_with("other-owner", ARTIFACT, "active")
        assert result is mock_stream


# ── _LazyArtifactHandle ───────────────────────────────────────────────────────

class TestLazyArtifactHandle:

    def test_resolve_deferred_until_get_bytes(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)
        # cacheable=True so get_bytes() doesn't trigger a second download
        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_ONLY, preload=False, cacheable=True)
        lazy = _LazyArtifactHandle(d, manager)
        # No download yet
        client.download.assert_not_called()
        # Trigger resolution
        lazy.get_bytes()
        client.download.assert_called_once()

    def test_resolve_deferred_until_get_string(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched(data=b"text")
        manager = make_manager(client)
        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_ONLY, preload=False, cacheable=True)
        lazy = _LazyArtifactHandle(d, manager)
        client.download.assert_not_called()
        assert lazy.get_string() == "text"

    def test_resolve_happens_only_once(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)
        # cacheable=True so repeated get_bytes() calls don't re-download
        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_ONLY, preload=False, cacheable=True)
        lazy = _LazyArtifactHandle(d, manager)
        lazy.get_bytes()
        lazy.get_bytes()
        lazy.get_bytes()
        assert client.download.call_count == 1

    def test_is_ready_false_before_resolve(self):
        manager = make_manager()
        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_ONLY, preload=False)
        lazy = _LazyArtifactHandle(d, manager)
        assert lazy.is_ready() is False

    def test_is_ready_true_after_resolve(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)
        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_ONLY, preload=False)
        lazy = _LazyArtifactHandle(d, manager)
        lazy.get_bytes()
        assert lazy.is_ready() is True

    def test_pending_callbacks_forwarded_after_resolve(self):
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)
        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_ONLY, preload=False)
        lazy = _LazyArtifactHandle(d, manager)
        received = []
        lazy.on_update(lambda e: received.append(e.new_version))
        # Callback not yet forwarded — resolve first
        lazy.get_bytes()
        # Simulate a push notification
        from deltafi.artifact import _CacheKey
        new_manifest = ArtifactManifest("v2", None, None, None, None)
        new_fetched = make_fetched(manifest=new_manifest)
        manager.refresh_if_cached(PLUGIN, ARTIFACT, "active")
        client.download.return_value = new_fetched
        evt = VersionChangedEvent(SOURCE_PREFIX, ARTIFACT, "active", VERSION, "v2")
        manager.invoke_callbacks(PLUGIN, ARTIFACT, "active", evt)
        assert received == ["v2"]

    def test_get_artifact_name_before_resolve(self):
        manager = make_manager()
        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_ONLY, preload=False)
        lazy = _LazyArtifactHandle(d, manager)
        assert lazy.get_artifact_name() == ARTIFACT


# ── Seed-on-missing returns real handle ───────────────────────────────────────

class TestSeedOnMissing:

    def test_seed_returns_real_handle_with_local_bytes(self, tmp_path):
        """After seeding a default resource, resolve returns a real ArtifactHandle
        using the local file bytes and server-provided manifest."""
        default_file = tmp_path / "default.json"
        default_file.write_bytes(b'{"key": "value"}')

        seeded_manifest = ArtifactManifest("20260319.171400.000", None, "application/octet-stream", "sha256abc", {"default": "true"})
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = None  # artifact not in registry
        client.seed_if_missing.return_value = seeded_manifest
        manager = make_manager(client)

        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_FIRST, cacheable=True, default_resource=str(default_file))
        handle = manager.resolve(d)

        assert isinstance(handle, ArtifactHandle)
        assert handle.is_ready() is True
        assert handle.get_bytes() == b'{"key": "value"}'
        assert handle.is_default() is True
        assert handle.get_version() == "20260319.171400.000"
        assert handle.get_sha256() == "sha256abc"
        client.seed_if_missing.assert_called_once()
        # 2 downloads (plugin miss + shared miss), no re-download after seed
        assert client.download.call_count == 2

    def test_seed_handle_supports_on_update(self, tmp_path):
        """on_update callbacks registered on a seeded handle fire on version change."""
        default_file = tmp_path / "default.json"
        default_file.write_bytes(b'{}')

        seeded_manifest = ArtifactManifest("v1", None, "application/octet-stream", "sha1", {"default": "true"})
        v2_manifest = ArtifactManifest("v2", "updated", "application/json", "sha2", None)
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.side_effect = [
            None,  # plugin miss
            None,  # shared miss
            make_fetched(v2_manifest, SOURCE_PREFIX, b'{"updated": true}'),  # refresh
        ]
        client.seed_if_missing.return_value = seeded_manifest
        manager = make_manager(client)

        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_FIRST, default_resource=str(default_file))
        handle = manager.resolve(d)

        versions = []
        handle.on_update(lambda evt: versions.append(evt.new_version))

        manager.refresh_if_cached(PLUGIN, ARTIFACT, "active")
        manager.invoke_callbacks(PLUGIN, ARTIFACT, "active", version_event("v2"))

        assert versions == ["v2"]

    def test_seed_failure_raises(self, tmp_path):
        """If seeding fails, resolve raises instead of returning a placeholder."""
        default_file = tmp_path / "default.json"
        default_file.write_bytes(b'{}')

        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = None
        client.seed_if_missing.return_value = None  # seed failed
        manager = make_manager(client)

        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_FIRST, default_resource=str(default_file))
        with pytest.raises(RuntimeError, match="Failed to seed"):
            manager.resolve(d)


# ── Lazy handle with missing artifact ─────────────────────────────────────────

class TestLazyMissing:

    def test_lazy_handle_raises_on_missing_artifact(self):
        """Lazy handle raises RuntimeError when first accessed if artifact doesn't exist."""
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = None
        manager = make_manager(client)

        d = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_ONLY, preload=False)
        lazy = _LazyArtifactHandle(d, manager)

        with pytest.raises(RuntimeError, match="Artifact not found"):
            lazy.get_bytes()


# ── Startup callbacks ─────────────────────────────────────────────────────────

class TestStartupCallbacks:

    def test_fire_startup_callbacks_invokes_all_registered(self):
        """fire_startup_callbacks fires each registered callback with a synthetic event."""
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        events = []
        handle.on_update(lambda evt: events.append(evt))

        manager.fire_startup_callbacks()

        assert len(events) == 1
        evt = events[0]
        assert evt.previous_version is None
        assert evt.new_version == VERSION
        assert evt.artifact_name == ARTIFACT
        assert evt.alias == "active"

    def test_fire_startup_callbacks_zero_arg_callback(self):
        """Zero-arg callbacks work with startup fire."""
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        calls = []
        handle.on_update(lambda: calls.append("fired"))

        manager.fire_startup_callbacks()

        assert calls == ["fired"]

    def test_fire_startup_callbacks_no_callbacks_is_noop(self):
        """fire_startup_callbacks does not raise when no callbacks are registered."""
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        manager.resolve(descriptor())
        # No on_update registered — should not raise
        manager.fire_startup_callbacks()

    def test_fire_startup_callbacks_failing_callback_does_not_block_others(self):
        """A failing startup callback doesn't prevent others from running."""
        client = MagicMock(spec=ArtifactRegistryClient)
        client.download.return_value = make_fetched()
        manager = make_manager(client)

        handle = manager.resolve(descriptor())
        calls = []
        handle.on_update(lambda evt: (_ for _ in ()).throw(RuntimeError("boom")))
        handle.on_update(lambda evt: calls.append("survived"))

        manager.fire_startup_callbacks()

        assert calls == ["survived"]

    def test_fire_startup_callbacks_multiple_artifacts(self):
        """Startup callbacks fire for each distinct artifact entry."""
        client = MagicMock(spec=ArtifactRegistryClient)
        m1 = ArtifactManifest("v1", None, None, None, None)
        m2 = ArtifactManifest("v2", None, None, None, None)
        client.download.side_effect = [
            make_fetched(m1),
            make_fetched(m2, source_prefix="shared", data=b"other"),
        ]
        manager = make_manager(client)

        d1 = Artifact(ARTIFACT, scope=ArtifactScope.PLUGIN_ONLY)
        d2 = Artifact("other-artifact", scope=ArtifactScope.SHARED_ONLY)
        h1 = manager.resolve(d1)
        h2 = manager.resolve(d2)

        versions = []
        h1.on_update(lambda evt: versions.append(("h1", evt.new_version)))
        h2.on_update(lambda evt: versions.append(("h2", evt.new_version)))

        manager.fire_startup_callbacks()

        assert ("h1", "v1") in versions
        assert ("h2", "v2") in versions
        assert len(versions) == 2
