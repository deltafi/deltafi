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
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AutowireCandidateResolver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginArtifactAutowireCandidateResolverTest {

    @Mock
    BeanFactory beanFactory;

    @Mock
    AutowireCandidateResolver delegate;

    @Mock
    ArtifactFactory artifactFactory;

    @Mock
    ArtifactHandle resolvedHandle;

    PluginArtifactAutowireCandidateResolver resolver;

    /** Captures the descriptor the resolver assembled from the @PluginArtifact annotation. */
    final AtomicReference<ArtifactDescriptor> requested = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        resolver = new PluginArtifactAutowireCandidateResolver(beanFactory, delegate);
    }

    // Stub the factory so artifact(name) returns a real Builder backed by a capturing resolver.
    private void stubArtifactFactory() {
        when(beanFactory.getBean(ArtifactFactory.class)).thenReturn(artifactFactory);
        when(artifactFactory.artifact(anyString())).thenAnswer(invocation ->
                new ArtifactFactory.Builder("test", invocation.getArgument(0, String.class),
                        descriptor -> {
                            requested.set(descriptor);
                            return resolvedHandle;
                        }));
    }

    @Test
    void getSuggestedValue_annotatedField_callsFactory() throws Exception {
        stubArtifactFactory();
        Field annotatedField = SampleBean.class.getDeclaredField("model");
        DependencyDescriptor descriptor = new DependencyDescriptor(annotatedField, true);

        Object result = resolver.getSuggestedValue(descriptor);

        assertThat(result).isSameAs(resolvedHandle);
        verifyNoInteractions(delegate);
    }

    @Test
    void getSuggestedValue_unannotatedField_delegatesToExisting() throws Exception {
        Field plainField = SampleBean.class.getDeclaredField("someString");
        DependencyDescriptor descriptor = new DependencyDescriptor(plainField, false);

        Object delegateResult = new Object();
        when(delegate.getSuggestedValue(descriptor)).thenReturn(delegateResult);

        Object result = resolver.getSuggestedValue(descriptor);

        assertThat(result).isSameAs(delegateResult);
        verifyNoInteractions(artifactFactory);
    }

    @Test
    void getSuggestedValue_annotatedField_passesCorrectAnnotation() throws Exception {
        stubArtifactFactory();
        Field annotatedField = SampleBean.class.getDeclaredField("model");
        DependencyDescriptor descriptor = new DependencyDescriptor(annotatedField, true);

        resolver.getSuggestedValue(descriptor);

        assertThat(requested.get().artifactName()).isEqualTo("my-model");
        assertThat(requested.get().scope()).isEqualTo(ArtifactScope.PLUGIN_FIRST);
        assertThat(requested.get().alias()).isEqualTo("active");
    }

    @Test
    void getSuggestedValue_annotatedConstructorParameter_callsFactory() throws Exception {
        stubArtifactFactory();
        Constructor<?> ctor = SampleBean.class.getDeclaredConstructor(ArtifactHandle.class, String.class);
        // parameter index 0 has @PluginArtifact
        DependencyDescriptor descriptor = new DependencyDescriptor(
                new org.springframework.core.MethodParameter(ctor, 0), true);

        Object result = resolver.getSuggestedValue(descriptor);

        assertThat(result).isSameAs(resolvedHandle);
        assertThat(requested.get().artifactName()).isEqualTo("my-model");
        verifyNoInteractions(delegate);
    }

    // ── Sample bean for reflection ─────────────────────────────────────────────
    @SuppressWarnings("unused")
    static class SampleBean {
        @PluginArtifact(artifactName = "my-model")
        ArtifactHandle model;

        String someString;

        SampleBean(@PluginArtifact(artifactName = "my-model") ArtifactHandle model, String someString) {
            this.model = model;
            this.someString = someString;
        }
    }
}
