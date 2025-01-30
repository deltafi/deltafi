/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry viewControllerRegistry) {
        viewControllerRegistry.addRedirectViewController("/docs", "/docs/index.html");
        viewControllerRegistry.addRedirectViewController("/docs/", "/docs/index.html");
        viewControllerRegistry.addRedirectViewController("/graphiql", "/graphiql/index.html");
        viewControllerRegistry.addRedirectViewController("/graphiql/", "/graphiql/index.html");

        // Forward routes handled by single-page app to "/" to handle fully-specified URLs
        viewControllerRegistry.addViewController("/deltafile/**").setViewName("forward:/");
        viewControllerRegistry.addViewController("/errors").setViewName("forward:/");
        viewControllerRegistry.addViewController("/metrics/**").setViewName("forward:/");
        viewControllerRegistry.addViewController("/config/**").setViewName("forward:/");
        viewControllerRegistry.addViewController("/admin/**").setViewName("forward:/");
        viewControllerRegistry.addViewController("/versions").setViewName("forward:/");
        viewControllerRegistry.addViewController("/events").setViewName("forward:/");
        viewControllerRegistry.addViewController("/unauthorized").setViewName("forward:/");
    }
}
