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
package org.deltafi.core.action.ingress;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.MappedKeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.subsystem.SubsystemFactory;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.deltafi.actionkit.action.ingress.IngressResult;
import org.deltafi.actionkit.action.ingress.IngressResultType;
import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.ssl.SslContextProvider;
import org.deltafi.common.test.content.InMemoryContentStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.IngressStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.ssl.PemSslBundleProperties;

import org.deltafi.actionkit.action.parameters.EnvVar;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SftpIngressTest {
    private static final ActionContentStorageService CONTENT_STORAGE_SERVICE =
            new InMemoryContentStorageService();

    private static SshServer sshServer;

    @BeforeAll
    static void beforeAll() throws UnrecoverableKeyException, CertificateException, KeyStoreException,
            IOException, NoSuchAlgorithmException {
        KeyPair keyPair = loadKeyPair();
        sshServer = SshServer.setUpDefaultServer();
        sshServer.setKeyPairProvider(new MappedKeyPairProvider(keyPair));
        sshServer.setPublickeyAuthenticator((s, publicKey, serverSession) -> true);
        sshServer.setPasswordAuthenticator((s, s1, serverSession) -> true);
        List<SubsystemFactory> subsystemFactories = new ArrayList<>();
        subsystemFactories.add(new SftpSubsystemFactory());
        sshServer.setSubsystemFactories(subsystemFactories);
        VirtualFileSystemFactory virtualFileSystemFactory = new VirtualFileSystemFactory();
        virtualFileSystemFactory.setDefaultHomeDir(Files.createTempDirectory("test"));
        sshServer.setFileSystemFactory(virtualFileSystemFactory);
        sshServer.start();

        try (SshClient sshClient = SshClient.setUpDefaultClient()) {
            sshClient.start();
            try (ClientSession clientSession = sshClient.connect(System.getProperty("user.name"), "localhost",
                    sshServer.getPort()).verify().getSession()) {
                clientSession.addPublicKeyIdentity(keyPair);
                clientSession.auth().verify();
                SftpClientFactory sftpClientFactory = SftpClientFactory.instance();
                SftpClient sftpClient = sftpClientFactory.createSftpClient(clientSession);
                sftpClient.mkdir("/test");
                putFile(sftpClient, "/test/1.a", "File contents #1");
                putFile(sftpClient, "/test/2.a", "File contents #2");
                putFile(sftpClient, "/test/3.b", "File contents #3");
                putFile(sftpClient, "/test/4.b", "File contents #4");
                putFile(sftpClient, "/test/5.c", "File contents #5");
                putFile(sftpClient, "/test/6.c", "File contents #6");
            }
        }
    }

    private static KeyPair loadKeyPair() throws CertificateException, KeyStoreException, IOException,
            NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance(new File("src/test/resources/mockKeystore.p12"),
                "password".toCharArray());
        String alias = keyStore.aliases().nextElement();
        PublicKey publicKey = keyStore.getCertificate(alias).getPublicKey();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, "password".toCharArray());
        return new KeyPair(publicKey, privateKey);
    }

    private static void putFile(SftpClient sftpClient, String filename, String contents) throws IOException {
        try (OutputStream outputStream = sftpClient.write(filename, SftpClient.OpenMode.Write,
                SftpClient.OpenMode.Create)) {
            outputStream.write(contents.getBytes());
        }
    }

    @AfterAll
    static void afterAll() throws IOException {
        sshServer.close();
    }

    @Test
    void ingresses() {
        SftpIngress sftpIngress = new SftpIngress(new JSch(), sslProperties());
        SftpIngress.Parameters params = new SftpIngress.Parameters();
        params.setHost("localhost");
        params.setPort(sshServer.getPort());
        params.setUsername(System.getProperty("user.name"));
        params.setDirectory("/test");
        params.setFileRegex(".*\\.a");
        IngressResultType ingressResultType = sftpIngress.ingress(
                ActionContext.builder()
                        .contentStorageService(CONTENT_STORAGE_SERVICE)
                        .did(UUID.randomUUID())
                        .deltaFileName("test-delta-file")
                        .dataSource("test-data-source")
                        .flowName("test-flow-name")
                        .build(), params);

        assertInstanceOf(IngressResult.class, ingressResultType);
        assertEquals(2, ((IngressResult) ingressResultType).getIngressResultItems().size());
    }

    @Test
    void ingressesUsingPassword() {
        SftpIngress sftpIngress = new SftpIngress(new JSch(), sslProperties());
        SftpIngress.Parameters params = new SftpIngress.Parameters();
        params.setHost("localhost");
        params.setPort(sshServer.getPort());
        params.setUsername(System.getProperty("user.name"));
        params.setPassword("the-password");
        params.setDirectory("/test");
        params.setFileRegex(".*\\.b");
        IngressResultType ingressResultType = sftpIngress.ingress(
                ActionContext.builder()
                        .contentStorageService(CONTENT_STORAGE_SERVICE)
                        .did(UUID.randomUUID())
                        .deltaFileName("test-delta-file")
                        .dataSource("test-data-source")
                        .flowName("test-flow-name")
                        .build(), params);

        assertInstanceOf(IngressResult.class, ingressResultType);
        assertEquals(2, ((IngressResult) ingressResultType).getIngressResultItems().size());
    }

    @Test
    void unhealthyStatusWithNoCredentials() {
        SftpIngress sftpIngress = new SftpIngress(new JSch(), new SslContextProvider(null));
        SftpIngress.Parameters params = new SftpIngress.Parameters();
        params.setHost("localhost");
        params.setPort(sshServer.getPort());
        params.setUsername(System.getProperty("user.name"));
        params.setDirectory("/test");
        params.setFileRegex(".*\\.c");
        IngressResultType ingressResultType = sftpIngress.ingress(
                ActionContext.builder()
                        .contentStorageService(CONTENT_STORAGE_SERVICE)
                        .did(UUID.randomUUID())
                        .deltaFileName("test-delta-file")
                        .dataSource("test-data-source")
                        .flowName("test-flow-name")
                        .build(), params);

        assertInstanceOf(IngressResult.class, ingressResultType);
        assertEquals(0, ((IngressResult) ingressResultType).getIngressResultItems().size());
        assertEquals(IngressStatus.UNHEALTHY, ((IngressResult) ingressResultType).getStatus());
        assertEquals("Password variable not set and no keystore is configured",
                ((IngressResult) ingressResultType).getStatusMessage());
    }

    @Test
    void unhealthyStatusOnException() throws JSchException {
        JSch mockJSch = Mockito.mock(JSch.class);
        Mockito.when(mockJSch.getSession(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt()))
                .thenThrow(new JSchException("Test error message"));
        SftpIngress sftpIngress = new SftpIngress(mockJSch, sslProperties());
        SftpIngress.Parameters params = new SftpIngress.Parameters();
        params.setHost("localhost");
        params.setPort(sshServer.getPort());
        params.setUsername(System.getProperty("user.name"));
        params.setDirectory("/test");
        params.setFileRegex(".*\\.c");
        IngressResultType ingressResultType = sftpIngress.ingress(
                ActionContext.builder()
                        .contentStorageService(CONTENT_STORAGE_SERVICE)
                        .did(UUID.randomUUID())
                        .deltaFileName("test-delta-file")
                        .dataSource("test-data-source")
                        .flowName("test-flow-name")
                        .build(), params);

        assertInstanceOf(IngressResult.class, ingressResultType);
        assertEquals(0, ((IngressResult) ingressResultType).getIngressResultItems().size());
        assertEquals(IngressStatus.UNHEALTHY, ((IngressResult) ingressResultType).getStatus());
        assertEquals("Unable to get files from SFTP server: Test error message",
                ((IngressResult) ingressResultType).getStatusMessage());
    }

    @Test
    void ingressesUsingPasswordEnvVar() {
        // Use an environment variable that exists in the test environment
        String envVarName = System.getenv().keySet().stream()
                .filter(k -> System.getenv(k) != null && !System.getenv(k).isEmpty())
                .findFirst()
                .orElse("PATH");  // PATH should always exist

        SftpIngress sftpIngress = new SftpIngress(new JSch(), sslProperties());
        SftpIngress.Parameters params = new SftpIngress.Parameters();
        params.setHost("localhost");
        params.setPort(sshServer.getPort());
        params.setUsername(System.getProperty("user.name"));
        params.setPasswordEnvVar(new EnvVar(envVarName));
        params.setDirectory("/test");
        params.setFileRegex(".*\\.c");
        IngressResultType ingressResultType = sftpIngress.ingress(
                ActionContext.builder()
                        .contentStorageService(CONTENT_STORAGE_SERVICE)
                        .did(UUID.randomUUID())
                        .deltaFileName("test-delta-file")
                        .dataSource("test-data-source")
                        .flowName("test-flow-name")
                        .build(), params);

        // The mock SSH server accepts any password, so this should succeed
        assertInstanceOf(IngressResult.class, ingressResultType);
        assertEquals(2, ((IngressResult) ingressResultType).getIngressResultItems().size());
    }

    @Test
    void unhealthyStatusWhenEnvVarNotSet() {
        SftpIngress sftpIngress = new SftpIngress(new JSch(), new SslContextProvider(null));
        SftpIngress.Parameters params = new SftpIngress.Parameters();
        params.setHost("localhost");
        params.setPort(sshServer.getPort());
        params.setUsername(System.getProperty("user.name"));
        params.setPasswordEnvVar(new EnvVar("NONEXISTENT_ENV_VAR_FOR_TESTING_12345"));
        params.setDirectory("/test");
        params.setFileRegex(".*\\.c");
        IngressResultType ingressResultType = sftpIngress.ingress(
                ActionContext.builder()
                        .contentStorageService(CONTENT_STORAGE_SERVICE)
                        .did(UUID.randomUUID())
                        .deltaFileName("test-delta-file")
                        .dataSource("test-data-source")
                        .flowName("test-flow-name")
                        .build(), params);

        assertInstanceOf(IngressResult.class, ingressResultType);
        assertEquals(IngressStatus.UNHEALTHY, ((IngressResult) ingressResultType).getStatus());
        assertTrue(((IngressResult) ingressResultType).getStatusMessage()
                .contains("NONEXISTENT_ENV_VAR_FOR_TESTING_12345"));
    }

    @Test
    void resolvePasswordReturnsEnvVarValueWhenSet() {
        // Use an env var that exists
        String envVarName = "PATH";
        String expectedValue = System.getenv(envVarName);

        SftpIngress.Parameters params = new SftpIngress.Parameters();
        params.setPasswordEnvVar(new EnvVar(envVarName));
        params.setPassword("deprecated-password");

        // Should use env var, not deprecated password
        assertEquals(expectedValue, params.resolvePassword());
    }

    @Test
    void resolvePasswordThrowsWhenEnvVarConfiguredButNotSet() {
        SftpIngress.Parameters params = new SftpIngress.Parameters();
        params.setPasswordEnvVar(new EnvVar("NONEXISTENT_ENV_VAR_FOR_TESTING_12345"));
        params.setPassword("deprecated-password");

        // Should throw, not fall back to deprecated password
        IllegalStateException exception = assertThrows(IllegalStateException.class, params::resolvePassword);
        assertTrue(exception.getMessage().contains("NONEXISTENT_ENV_VAR_FOR_TESTING_12345"));
    }

    @Test
    void resolvePasswordFallsBackToDeprecatedPasswordWhenEnvVarNotConfigured() {
        SftpIngress.Parameters params = new SftpIngress.Parameters();
        // passwordEnvVar is not set (default empty EnvVar)
        params.setPassword("deprecated-password");

        assertEquals("deprecated-password", params.resolvePassword());
    }

    @Test
    void resolvePasswordReturnsNullWhenNothingConfigured() {
        SftpIngress.Parameters params = new SftpIngress.Parameters();
        // Neither passwordEnvVar nor password is set

        assertNull(params.resolvePassword());
    }

    private SslContextProvider sslProperties() {
        PemSslBundleProperties properties = new PemSslBundleProperties();
        properties.getKey().setAlias("ssl");
        properties.setProtocol("TLSv1.2");
        properties.getKeystore().setPrivateKey("classpath:privatekey.pem");
        properties.getKeystore().setCertificate("classpath:cert.pem");
        properties.getTruststore().setCertificate("classpath:ca_chain.pem");
        return new SslContextProvider(properties);
    }
}
