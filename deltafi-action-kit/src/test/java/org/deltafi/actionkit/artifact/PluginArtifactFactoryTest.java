/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.actionkit.artifact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginArtifactFactoryTest {

    @Mock
    ArtifactCacheManager cacheManager;

    @Mock
    ArtifactHandle resolvedHandle;

    PluginArtifactFactory factory;

    @BeforeEach
    void setUp() {
        factory = new PluginArtifactFactory(cacheManager, "org.example.my-plugin");
    }

    @Test
    void create_preload_true_resolves_immediately() {
        when(cacheManager.resolve(any())).thenReturn(resolvedHandle);

        PluginArtifact annotation = getAnnotation("preloadField");
        ArtifactHandle result = factory.create(annotation);

        // Should have resolved immediately
        verify(cacheManager, times(1)).resolve(any());
        assertThat(result).isSameAs(resolvedHandle);
    }

    @Test
    void create_preload_false_defers_resolution_until_access() {
        when(cacheManager.resolve(any())).thenReturn(resolvedHandle);
        when(resolvedHandle.getBytes()).thenReturn(new byte[]{1, 2, 3});

        PluginArtifact annotation = getAnnotation("lazyField");
        ArtifactHandle result = factory.create(annotation);

        // cacheManager.resolve should NOT be called yet
        verify(cacheManager, never()).resolve(any());

        // Access triggers loading
        byte[] bytes = result.getBytes();
        verify(cacheManager, times(1)).resolve(any());
        assertThat(bytes).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void create_preload_false_resolves_only_once_on_repeated_access() {
        when(cacheManager.resolve(any())).thenReturn(resolvedHandle);
        when(resolvedHandle.getVersion()).thenReturn("v1");

        PluginArtifact annotation = getAnnotation("lazyField");
        ArtifactHandle result = factory.create(annotation);

        result.getVersion();
        result.getVersion();
        result.getVersion();

        // Should only resolve once despite multiple accesses
        verify(cacheManager, times(1)).resolve(any());
    }

    @PluginArtifact(artifactName = "my-model")
    private ArtifactHandle preloadField;

    @PluginArtifact(artifactName = "my-model", preload = false)
    private ArtifactHandle lazyField;

    private PluginArtifact getAnnotation(String fieldName) {
        try {
            return getClass().getDeclaredField(fieldName).getAnnotation(PluginArtifact.class);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
