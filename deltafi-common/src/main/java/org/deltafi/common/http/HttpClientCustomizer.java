/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.http;

import java.net.http.HttpClient;

/**
 * Create a bean implementing this class to customize the HttpClient
 * created in {@link HttpServiceAutoConfiguration}
 */
public interface HttpClientCustomizer {

    /**
     * Customize the HttpClient.Builder used to create HttpClient bean
     * By default, the builder will add an SSLContext based on {@link org.deltafi.common.ssl.SslProperties}
     * and set the connectTimeout to {@link HttpServiceAutoConfiguration#DEFAULT_CONNECT_TIMEOUT}.
     * @param builder the HttpClient.Builder used to build the HttpClient bean
     */
    void customize(HttpClient.Builder builder);
}
