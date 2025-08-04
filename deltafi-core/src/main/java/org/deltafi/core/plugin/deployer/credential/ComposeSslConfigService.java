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
package org.deltafi.core.plugin.deployer.credential;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.configuration.SslSecretNames;
import org.deltafi.core.services.CertificateInfoService;
import org.deltafi.core.services.SslConfigService;
import org.deltafi.core.types.KeyCertPair;
import org.deltafi.core.types.SslInfo;
import org.deltafi.core.types.SslSettings;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class ComposeSslConfigService implements SslConfigService {

    private static final Path CERTS = Path.of("/certs");
    private static final Path CA_CHAIN = CERTS.resolve(CA_CRT);
    private static final String ENV_FILE = ".env";
    public static final String KEY_PASSWORD = "KEY_PASSWORD";

    private final CertificateInfoService certificateInfoService;
    private final SslSecretNames sslSecretNames;

    /**
     * Get the KeyCertPair in the given secret
     *
     * @param secretName name of the secret to retrieve
     * @param required   throw an Exception if the secret is required but missing
     * @return the KeyCertPair
     */
    @Override
    public SslInfo getKeyCert(String secretName, boolean required) {
        validateName(secretName);
        Path secretDir = CERTS.resolve(secretName);

        if (!secretDir.toFile().exists()) {
            if (required) {
                throw new IllegalArgumentException(String.format("Secret '%s' does not exist", secretName));
            } else {
                return null;
            }
        }

        String tlsKey = readFile(secretDir.resolve(TLS_KEY));
        String tlsCert = readFile(secretDir.resolve(TLS_CRT));
        Properties props = readEnv(secretDir.resolve(ENV_FILE));
        KeyCertPair keyCertPair = new KeyCertPair(tlsKey, tlsCert, props.getProperty(KEY_PASSWORD));
        return certificateInfoService.getSslInfo(secretName, keyCertPair);
    }

    /**
     * Get the KeyCertPair in the given secret
     *
     * @param secretName name of the secret to retrieve
     * @return the KeyCertPair
     */
    @Override
    public SslInfo getKeyCert(String secretName) {
        return getKeyCert(secretName, true);
    }

    /**
     * Save a key/cert pair into the system. This will replace
     *
     * @param secretName  determines where the key/cert pair will be stored and retrieved from
     * @param keyCertPair key/cert contents
     * @return the saved value
     */
    @Override
    public SslInfo saveKeyCert(String secretName, KeyCertPair keyCertPair) {
        validateName(secretName);
        Path secretDir = CERTS.resolve(secretName);
        Path keyPath = secretDir.resolve(TLS_KEY);
        Path certPath = secretDir.resolve(TLS_CRT);

        writeFile(keyPath, keyCertPair.key());
        writeFile(certPath, keyCertPair.cert());

        StringBuilder builder = new StringBuilder();
        builder.append(envPair("KEY_PATH", keyPath));
        builder.append(envPair("CERT_PATH", certPath));
        builder.append(envPair("CA_CHAIN_PATH", CA_CHAIN));

        if (StringUtils.isNotBlank(keyCertPair.keyPassphrase())) {
            builder.append(envPair(KEY_PASSWORD, keyCertPair.keyPassphrase()));
        }

        writeFile(secretDir.resolve(ENV_FILE), builder.toString());
        copyCaChain(secretDir);
        return certificateInfoService.getSslInfo(secretName, keyCertPair);
    }

    private String envPair(String key, Path path) {
        return envPair(key, path.toFile().getAbsolutePath());
    }

    private String envPair(String key, String value) {
        return key + "=" + value + "\n";
    }

    /**
     * Delete the key/cert pair in the given secret
     *
     * @param secretName name of the secret to delete
     * @return the deleted KeyCertPair
     */
    @Override
    public SslInfo deleteKeyCert(String secretName) {
        validateName(secretName);
        SslInfo keyCertPair = getKeyCert(secretName);
        Path secretDir = CERTS.resolve(secretName);
        try {
            FileUtils.deleteDirectory(secretDir.toFile());
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Cannot remove Secret '%s'", secretName), e);
        }
        return keyCertPair;
    }

    /**
     * Add one or more cert entries to the existing ca-chain
     *
     * @param certs info to append to the ca-chain
     * @return the ca-chain with the appended values
     */
    @Override
    public String appendToCaChain(String certs) {
        String existingChain = getCaChain();

        String newChain = existingChain != null ? existingChain + "\n" + certs : certs;
        return saveCaChain(newChain);
    }

    /**
     * Load all the certs into the ca-chain. Any existing
     * entries will be replaced
     *
     * @param certs list of certs to save as the ca-chain
     * @return the ca-chain with the new values
     */
    @Override
    public String saveCaChain(String certs) {
        writeFile(CA_CHAIN, certs);
        copyCaChain();
        return certs;
    }

    /**
     * Get the full SSL picture including server keys, plugin keys and ca-chain
     *
     * @return running SslInfo including the active ca-chain and of key/certs
     */
    @Override
    public SslSettings getSslSettings() {
        Map<String, SslInfo> keyCertPairs = getSslInfoMap(sslSecretNames);

        try (Stream<Path> files = Files.list(CERTS)) {
            files.filter(this::isReadableDir).forEach(path -> addPluginPair(path, keyCertPairs));
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Cannot read contents of the '%s' directory", CERTS.getFileName().toString()), e);
        }

        return new SslSettings(keyCertPairs.values(), getCaChain());
    }

    void copyCaChain() {
        if (!Files.exists(CA_CHAIN)) {
            return;
        }

        try (Stream<Path> secretDirs = Files.list(CERTS)) {
            secretDirs.filter(this::isReadableDir).forEach(this::copyCaChain);
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Cannot read contents of the '%s' directory", CERTS.getFileName().toString()), e);
        }
    }

    void copyCaChain(Path path) {
        // skip if the ca-chain doesn't exist or the symbolic link already exists
        if (!Files.exists(CA_CHAIN) || Files.exists(path.resolve(CA_CRT))) {
            return;
        }

        try (Stream<Path> files = Files.list(path)) {
            // ignore empty directories that were auto-created by docker/compose
            // this is needed so older plugins don't fail for missing tls.key/crt files
            if (files.findFirst().isPresent()) {
                Files.copy(CA_CHAIN, path.resolve(CA_CRT));
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Cannot copy the ca.crt file '%s'", path), e);
        }
    }

    private void addPluginPair(Path path, Map<String, SslInfo> keyCertPairs) {
        keyCertPairs.computeIfAbsent(path.getFileName().toString(), this::newSslInfo).usedBy().add("plugins");
    }

    private boolean isReadableDir(Path path) {
        return Files.isDirectory(path) && Files.isReadable(path);
    }

    String getCaChain() {
        return readFile(CA_CHAIN);
    }

    private Properties readEnv(Path path) {
        Properties properties = new Properties();
        if (Files.exists(path) && Files.isReadable(path)) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                properties.load(fis);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read the file at " + path, e);
            }
        }
        return properties;
    }

    private String readFile(Path path) {
        try {
            if (Files.exists(path) && Files.isReadable(path)) {
                return Files.readString(path, Charset.defaultCharset());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the file at " + path, e);
        }

        return null;
    }

    private void writeFile(Path path, String content) {
        try {
            if (content == null) {
                return;
            }
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(path.getParent());
            }

            Files.writeString(path, content);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the file at " + path, e);
        }
    }

    private void validateName(String secretName) {
        if (StringUtils.isBlank(secretName)) {
            throw new IllegalArgumentException("Secret name is empty");
        }

        // reject relative paths
        String normalized = FilenameUtils.normalize(secretName);
        if (normalized == null || !normalized.equals(secretName)) {
            throw new IllegalArgumentException(String.format("Secret name '%s' is invalid", secretName));
        }

        // reject any path separators
        String name = FilenameUtils.getName(secretName);
        if (!name.equals(secretName)) {
            throw new IllegalArgumentException(String.format("Secret name '%s' is invalid", secretName));
        }
    }
}
