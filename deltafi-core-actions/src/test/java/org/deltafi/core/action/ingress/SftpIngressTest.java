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
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.ssl.SslProperties;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.IngressStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class SftpIngressTest {
    private static final ContentStorageService CONTENT_STORAGE_SERVICE =
            new ContentStorageService(new InMemoryObjectStorageService());

    private static SshServer sshServer;

    @BeforeAll
    public static void beforeAll() throws UnrecoverableKeyException, CertificateException, KeyStoreException,
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
    public static void afterAll() throws IOException {
        sshServer.close();
    }

    @Test
    public void ingresses() {
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
    public void ingressesUsingPassword() {
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
    public void unhealthyStatusWithNoCredentials() {
        SftpIngress sftpIngress = new SftpIngress(new JSch(), new SslProperties());
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
    public void unhealthyStatusOnException() throws JSchException {
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

    private SslProperties sslProperties() {
        SslProperties sslProperties = new SslProperties();
        sslProperties.setKeyStore("src/test/resources/mockKeystore.p12");
        sslProperties.setKeyStorePassword("password");
        sslProperties.setKeyStoreType("PKCS12");
        sslProperties.setTrustStore("src/test/resources/mockTrustStore.jks");
        sslProperties.setTrustStorePassword("storePassword");
        sslProperties.setTrustStoreType("JKS");
        sslProperties.setProtocol("TLSv1.2");
        return sslProperties;
    }
}
