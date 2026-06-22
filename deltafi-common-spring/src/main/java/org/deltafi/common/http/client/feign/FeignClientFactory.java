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
package org.deltafi.common.http.client.feign;

import feign.*;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson3.Jackson3Decoder;
import feign.jackson3.Jackson3Encoder;
import feign.slf4j.Slf4jLogger;
import org.deltafi.common.ssl.SslCredentials;

import java.util.List;

/**
 * Factory for building Feign HTTP clients with optional SSL support.
 */
public class FeignClientFactory {
    /**
     * Builds a client for the provided Feign-annotated interface. Methods of the interface will accept a URI to
     * access.
     *
     * @param clientClass the Feign-annotated interface class
     * @param <T> the interface type
     * @return a Feign-generated instance of the provided interface
     */
    public static <T> T build(Class<T> clientClass) {
        return build(clientClass, null, null, null, null);
    }

    /**
     * Builds a client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param clientClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @param <T> the interface type
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     */
    public static <T> T build(Class<T> clientClass, String url) {
        return build(clientClass, url, null, null, null);
    }

    /**
     * Builds a client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param clientClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @param encoder the encoder used to encode instances to strings
     * @param decoder the decoder used to decode strings to instances
     * @param retryer the retryer used on failed requests
     * @param <T> the interface type
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     */
    public static <T> T build(Class<T> clientClass, String url, Encoder encoder, Decoder decoder, Retryer retryer) {
        return build(clientClass, url, encoder, decoder, retryer, (Request.Options) null);
    }

    /**
     * Builds a client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param clientClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @param encoder the encoder used to encode instances to strings
     * @param decoder the decoder used to decode strings to instances
     * @param retryer the retryer used on failed requests
     * @param options additional options for requests
     * @param <T> the interface type
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     */
    public static <T> T build(Class<T> clientClass, String url, Encoder encoder, Decoder decoder, Retryer retryer,
            Request.Options options) {
        return build(createBuilder(clientClass, encoder, decoder, retryer, options), clientClass, url);
    }

    /**
     * Builds an SSL-enabled client for the provided Feign-annotated interface. Methods of the interface will accept a
     * URI to access.
     *
     * @param clientClass the Feign-annotated interface class
     * @param sslCredentials the SslCredentials used to build an SSL context
     * @param <T> the interface type
     * @return a Feign-generated instance of the provided interface
     */
    public static <T> T build(Class<T> clientClass, SslCredentials sslCredentials) {
        return build(clientClass, null, null, null, null, sslCredentials);
    }

    /**
     * Builds an SSL-enabled client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param clientClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @param sslCredentials the SslCredentials used to build an SSL context
     * @param <T> the interface type
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     */
    public static <T> T build(Class<T> clientClass, String url, SslCredentials sslCredentials) {
        return build(clientClass, url, null, null, null, sslCredentials);
    }

    /**
     * Builds an SSL-enabled client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param clientClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @param encoder the encoder used to encode instances to strings
     * @param decoder the decoder used to decode strings to instances
     * @param retryer the retryer used on failed requests
     * @param sslCredentials the SslCredentials used to build an SSL context
     * @param <T> the interface type
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     */
    public static <T> T build(Class<T> clientClass, String url, Encoder encoder, Decoder decoder, Retryer retryer,
            SslCredentials sslCredentials) {
        return build(clientClass, url, encoder, decoder, retryer, null, sslCredentials);
    }

    /**
     * Builds an SSL-enabled client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param clientClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @param encoder the encoder used to encode instances to strings
     * @param decoder the decoder used to decode strings to instances
     * @param retryer the retryer used on failed requests
     * @param options additional options for requests
     * @param sslCredentials the SslCredentials used to build an SSL context
     * @param <T> the interface type
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     */
    public static <T> T build(Class<T> clientClass, String url, Encoder encoder, Decoder decoder, Retryer retryer,
            Request.Options options, SslCredentials sslCredentials) {
        Feign.Builder builder = createBuilder(clientClass, encoder, decoder, retryer, options);

        if (sslCredentials != null && sslCredentials.isConfigured()) {
            builder.client(new DefaultClient(sslCredentials.createSslContext().getSocketFactory(), null));
        }

        return build(builder, clientClass, url);
    }

    private static <T> Feign.Builder createBuilder(Class<T> clientClass, Encoder encoder, Decoder decoder,
            Retryer retryer, Request.Options options) {
        return Feign.builder()
                .encoder(encoder == null ? new Jackson3Encoder(List.of()) : encoder)
                .decoder(decoder == null ? new Jackson3Decoder(List.of()) : decoder)
                .retryer(retryer == null ? new DefaultRetryer(1000, 5000, 5) : retryer)
                .options(options == null ? new Request.Options() : options)
                .logger(new Slf4jLogger(clientClass))
                .logLevel(Logger.Level.FULL);
    }

    private static <T> T build(Feign.Builder builder, Class<T> clientClass, String url) {
        return url == null ? builder.target(Target.EmptyTarget.create(clientClass)) : builder.target(clientClass, url);
    }
}
