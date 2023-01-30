/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.http.client.feign;

import feign.Client;
import feign.Feign;
import feign.Logger;
import feign.Retryer;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

import javax.net.ssl.SSLContext;

public class FeignClientFactory {
    /**
     * Builds a client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param targetClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     * @param <T> the interface type
     */
    public static <T> T build(Class<T> targetClass, String url) {
        return build(targetClass, url, null);
    }

    /**
     * Builds an SSL-enabled client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param targetClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @param sslContext the SSLContext to use for creating an SSL connection to the given URL
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     * @param <T> the interface type
     */
    public static <T> T build(Class<T> targetClass, String url, SSLContext sslContext) {
        return build(targetClass, url, sslContext, new Retryer.Default(1000, 5000, 5));
    }

    /**
     * Builds an SSL-enabled client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param targetClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @param sslContext the SSLContext to use for creating an SSL connection to the given URL
     * @param retryer the retry settings for this Feign client
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     * @param <T> the interface type
     */
    public static <T> T build(Class<T> targetClass, String url, SSLContext sslContext, Retryer retryer) {
        Feign.Builder builder = Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .retryer(retryer)
                .logger(new Slf4jLogger(targetClass))
                .logLevel(Logger.Level.FULL);

        if (sslContext != null) {
            builder.client(new Client.Default(sslContext.getSocketFactory(), null));
        }

        return builder.target(targetClass, url);
    }
}
