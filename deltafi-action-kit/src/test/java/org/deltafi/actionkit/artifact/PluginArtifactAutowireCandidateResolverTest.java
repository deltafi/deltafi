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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginArtifactAutowireCandidateResolverTest {

    @Mock
    BeanFactory beanFactory;

    @Mock
    PluginArtifactFactory factory;

    @Mock
    AutowireCandidateResolver delegate;

    @Mock
    ArtifactHandle factoryHandle;

    PluginArtifactAutowireCandidateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PluginArtifactAutowireCandidateResolver(beanFactory, delegate);
    }

    @Test
    void getSuggestedValue_annotatedField_callsFactory() throws Exception {
        Field annotatedField = SampleBean.class.getDeclaredField("model");
        DependencyDescriptor descriptor = new DependencyDescriptor(annotatedField, true);

        when(beanFactory.getBean(PluginArtifactFactory.class)).thenReturn(factory);
        when(factory.create(any())).thenReturn(factoryHandle);

        Object result = resolver.getSuggestedValue(descriptor);

        assertThat(result).isSameAs(factoryHandle);
        verify(factory).create(any(PluginArtifact.class));
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
        verifyNoInteractions(factory);
    }

    @Test
    void getSuggestedValue_annotatedField_passesCorrectAnnotation() throws Exception {
        Field annotatedField = SampleBean.class.getDeclaredField("model");
        DependencyDescriptor descriptor = new DependencyDescriptor(annotatedField, true);

        when(beanFactory.getBean(PluginArtifactFactory.class)).thenReturn(factory);
        when(factory.create(any())).thenReturn(factoryHandle);
        resolver.getSuggestedValue(descriptor);

        verify(factory).create(argThat(annotation ->
                "my-model".equals(annotation.artifactName())
                        && annotation.scope() == ArtifactScope.PLUGIN_FIRST
                        && "active".equals(annotation.alias())
        ));
    }

    @Test
    void getSuggestedValue_annotatedConstructorParameter_callsFactory() throws Exception {
        Constructor<?> ctor = SampleBean.class.getDeclaredConstructor(ArtifactHandle.class, String.class);
        // parameter index 0 has @PluginArtifact
        DependencyDescriptor descriptor = new DependencyDescriptor(
                new org.springframework.core.MethodParameter(ctor, 0), true);

        when(beanFactory.getBean(PluginArtifactFactory.class)).thenReturn(factory);
        when(factory.create(any())).thenReturn(factoryHandle);

        Object result = resolver.getSuggestedValue(descriptor);

        assertThat(result).isSameAs(factoryHandle);
        verify(factory).create(argThat(a -> "my-model".equals(a.artifactName())));
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
