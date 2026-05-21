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
package org.deltafi.common.ssl;

import org.deltafi.common.constant.DeprecatedSince;
import org.springframework.boot.autoconfigure.ssl.PemSslBundleProperties;
import org.springframework.boot.autoconfigure.ssl.PropertiesSslBundle;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.KeyStore;
import java.util.Objects;

/**
 * Spring-coupled {@link SslCredentials} built from {@link PemSslBundleProperties}. Retains the
 * originating {@link SslBundle} so callers in the Spring ecosystem can still reach for it during
 * migration.
 *
 * <p>In 3.0 this class is removed. {@link SslAutoConfiguration} will produce {@link SslCredentials}
 * directly; consumers that need a Spring {@link SslBundle} should construct one themselves from
 * {@link PemSslBundleProperties} via {@link PropertiesSslBundle}.
 *
 * @deprecated Use {@link SslCredentials} as the injected type. Will be removed in 3.0.
 */
@Deprecated(since = DeprecatedSince.VERSION_2_55_1, forRemoval = true)
public class SslContextProvider extends SslCredentials {

    private final SslBundle bundle;

    /**
     * Construct from PEM SSL bundle properties. If {@code properties} is null or no key store is
     * configured, the resulting provider reports {@link #isConfigured()} as false.
     *
     * @deprecated Construct {@link SslCredentials} directly or let {@link SslAutoConfiguration}
     * build the bean. Will be removed in 3.0.
     */
    @Deprecated(since = DeprecatedSince.VERSION_2_55_1, forRemoval = true)
    public SslContextProvider(PemSslBundleProperties properties) {
        this(PropertiesSslBundle.get(Objects.requireNonNullElseGet(properties, PemSslBundleProperties::new)));
    }

    /**
     * Construct directly from pre-built SSL key/trust material. Preserves the original
     * {@code deltafi-common} {@code SslContextProvider} constructor for strict backward
     * compatibility; the resulting provider has no originating Spring {@link SslBundle}
     * ({@link #getBundle()} returns null).
     *
     * @deprecated Construct {@link SslCredentials} directly. Will be removed in 3.0.
     */
    @Deprecated(since = DeprecatedSince.VERSION_2_55_1, forRemoval = true)
    public SslContextProvider(SSLContext sslContext, TrustManager[] trustManagers,
                              KeyStore keyStore, String keyAlias, char[] keyPassword) {
        super(sslContext, trustManagers, keyStore, keyAlias, keyPassword);
        this.bundle = null;
    }

    /**
     * An unconfigured provider. Preserves the original {@code deltafi-common}
     * {@code SslContextProvider.unconfigured()} for strict backward compatibility — hides
     * {@link SslCredentials#unconfigured()} so the static return type remains
     * {@code SslContextProvider}.
     *
     * @deprecated Use {@link SslCredentials#unconfigured()}. Will be removed in 3.0.
     */
    @Deprecated(since = DeprecatedSince.VERSION_2_55_1, forRemoval = true)
    public static SslContextProvider unconfigured() {
        return new SslContextProvider(null, null, null, null, null);
    }

    private SslContextProvider(SslBundle bundle) {
        this(bundle, bundle.getStores().getKeyStore());
    }

    private SslContextProvider(SslBundle bundle, KeyStore keyStore) {
        super(
                keyStore == null ? null : bundle.createSslContext(),
                keyStore == null ? null : bundle.getManagers().getTrustManagers(),
                keyStore,
                keyStore == null ? null : bundle.getKey().getAlias(),
                keyStore == null ? null : passwordChars(bundle.getKey())
        );
        this.bundle = bundle;
    }

    private static char[] passwordChars(SslBundleKey key) {
        return key.getPassword() != null ? key.getPassword().toCharArray() : null;
    }

    /**
     * @return the underlying Spring {@link SslBundle}, or null if this provider is unconfigured.
     * @deprecated Use {@link #getSslContext()}, {@link #getKeyStore()}, {@link #getTrustManagers()},
     * {@link #getKeyAlias()}, and {@link #getKeyPassword()} on {@link SslCredentials} directly.
     * Will be removed in 3.0.
     */
    @Deprecated(since = DeprecatedSince.VERSION_2_55_1, forRemoval = true)
    public SslBundle getBundle() {
        return bundle;
    }
}
