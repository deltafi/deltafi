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

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;

/**
 * Spring {@link AutowireCandidateResolver} that intercepts injection points annotated with
 * {@link PluginArtifact} and delegates them to {@link PluginArtifactFactory}.
 *
 * <p>All other injection points are forwarded to the original resolver that was in place
 * before this one was installed, preserving standard Spring autowiring behaviour.
 *
 * <p>Registered by the action-kit auto-configuration via
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor}.
 * The factory is looked up lazily from the {@link BeanFactory} on first use, so that
 * installation can happen early in the Spring lifecycle before artifact infrastructure
 * beans are created.
 */
class PluginArtifactAutowireCandidateResolver
        extends ContextAnnotationAutowireCandidateResolver {

    private final BeanFactory beanFactory;
    private final AutowireCandidateResolver delegate;

    PluginArtifactAutowireCandidateResolver(
            BeanFactory beanFactory,
            AutowireCandidateResolver delegate) {

        this.beanFactory = beanFactory;
        this.delegate = delegate;
    }

    @Override
    public Object getSuggestedValue(DependencyDescriptor descriptor) {

        // descriptor.getAnnotations() returns parameter annotations for constructor/method
        // injection, and field annotations for field injection — unlike getAnnotatedElement()
        // which returns the Constructor/Method itself (not the Parameter) for non-field cases.
        PluginArtifact annotation = null;
        for (java.lang.annotation.Annotation a : descriptor.getAnnotations()) {
            if (a instanceof PluginArtifact pa) {
                annotation = pa;
                break;
            }
        }

        if (annotation != null) {
            // Resolve through the same public ArtifactFactory seam a non-Spring plugin would use.
            return beanFactory.getBean(ArtifactFactory.class)
                    .artifact(annotation.artifactName())
                    .scope(annotation.scope())
                    .alias(annotation.alias())
                    .cacheable(annotation.cacheable())
                    .preload(annotation.preload())
                    .defaultResource(annotation.defaultResource())
                    .build();
        }

        // Delegate back to original resolver
        return delegate.getSuggestedValue(descriptor);
    }

    @Override
    public boolean isAutowireCandidate(
            org.springframework.beans.factory.config.@NonNull BeanDefinitionHolder bdHolder,
            @NonNull DependencyDescriptor descriptor) {

        return delegate.isAutowireCandidate(bdHolder, descriptor);
    }

    @Override
    public Object getLazyResolutionProxyIfNecessary(
            @NonNull DependencyDescriptor descriptor,
            String beanName) {

        return delegate.getLazyResolutionProxyIfNecessary(descriptor, beanName);
    }
}