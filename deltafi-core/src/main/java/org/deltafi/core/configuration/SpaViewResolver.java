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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@Component
public class SpaViewResolver implements ErrorViewResolver {

    /**
     * Resolve an error view for the specified details.
     *
     * @param request the source request
     * @param status  the http status of the error
     * @param model   the suggested model to be used with the view
     * @return a resolved {@link ModelAndView} or {@code null}
     */
    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        // delegate to the UI for any requests that were not mapped in Spring
        if (status == HttpStatus.NOT_FOUND) {
            String path = request.getRequestURI();
            // Don't forward file requests (with extensions)
            if (!path.contains(".")) {
                return new ModelAndView("forward:/");
            }
        }
        return null;
    }
}
