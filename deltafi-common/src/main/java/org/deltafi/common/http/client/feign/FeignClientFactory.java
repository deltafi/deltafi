/*
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

import feign.*;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import org.deltafi.common.ssl.SslContextFactory;
import org.deltafi.common.ssl.SslProperties;

import javax.net.ssl.SSLContext;
import java.io.File;

public class FeignClientFactory {
    /**
     * Builds a client for the provided Feign-annotated interface. Methods of the interface will accept a URI to access.
     *
     * @param clientClass the Feign-annotated interface class
     * @return a Feign-generated instance of the provided interface
     * @param <T> the interface type
     */
    public static <T> T build(Class<T> clientClass) {
        return build(clientClass, null, null, null, null);
    }

    /**
     * Builds a client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param clientClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     * @param <T> the interface type
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
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     * @param <T> the interface type
     */
    public static <T> T build(Class<T> clientClass, String url, Encoder encoder, Decoder decoder, Retryer retryer) {
        return build(createBuilder(clientClass, encoder, decoder, retryer), clientClass, url);
    }

    /**
     * Builds an SSL-enabled client for the provided Feign-annotated interface. Methods of the interface will accept a
     * URI to access.
     *
     * @param clientClass the Feign-annotated interface class
     * @param sslProperties the SSL properties used to build an SSL context
     * @return a Feign-generated instance of the provided interface
     * @param <T> the interface type
     */
    public static <T> T build(Class<T> clientClass, SslProperties sslProperties) throws SslContextFactory.SslException {
        return build(clientClass, null, null, null, null, sslProperties);
    }

    /**
     * Builds an SSL-enabled client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param clientClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @param sslProperties the SSL properties used to build an SSL context
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     * @param <T> the interface type
     */
    public static <T> T build(Class<T> clientClass, String url, SslProperties sslProperties) throws SslContextFactory.SslException {
        return build(clientClass, url, null, null, null, sslProperties);
    }

    /**
     * Builds an SSL-enabled client for the provided Feign-annotated interface for the resources at the given URL.
     *
     * @param clientClass the Feign-annotated interface class
     * @param url the URL the client will access
     * @param encoder the encoder used to encode instances to strings
     * @param decoder the decoder used to decode strings to instances
     * @param retryer the retryer used on failed requests
     * @param sslProperties the SSL properties used to build an SSL context
     * @return a Feign-generated instance of the provided interface for accessing the given URL
     * @param <T> the interface type
     */
    public static <T> T build(Class<T> clientClass, String url, Encoder encoder, Decoder decoder, Retryer retryer,
            SslProperties sslProperties) throws SslContextFactory.SslException {
        Feign.Builder builder = createBuilder(clientClass, encoder, decoder, retryer);

        if ((sslProperties != null) && keystoreExists(sslProperties.getKeyStore())) {
            SSLContext context = SslContextFactory.buildSslContext(sslProperties);
            builder.client(new Client.Default(context.getSocketFactory(), null));
        }

        return build(builder, clientClass, url);
    }

    private static <T> Feign.Builder createBuilder(Class<T> clientClass, Encoder encoder, Decoder decoder, Retryer retryer) {
        return Feign.builder()
            .encoder(encoder == null ? new JacksonEncoder() : encoder)
            .decoder(decoder == null ? new JacksonDecoder() : decoder)
            .retryer(retryer == null ? new Retryer.Default(1000, 5000, 5) : retryer)
            .logger(new Slf4jLogger(clientClass))
            .logLevel(Logger.Level.FULL);
    }

    private static <T> T build(Feign.Builder builder, Class<T> clientClass, String url) {
        return url == null ? builder.target(Target.EmptyTarget.create(clientClass)) : builder.target(clientClass, url);
    }

    private static boolean keystoreExists(String path) {
        return (path != null) && new File(path).exists();
    }
}
