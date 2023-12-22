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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.jcraft.jsch.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.deltafi.actionkit.action.ingress.IngressResult;
import org.deltafi.actionkit.action.ingress.IngressResultItem;
import org.deltafi.actionkit.action.ingress.IngressResultType;
import org.deltafi.actionkit.action.ingress.TimedIngressAction;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.ssl.SslProperties;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.IngressStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Vector;

@Component
@Slf4j
public class SftpTimedIngressAction extends TimedIngressAction<SftpTimedIngressAction.Parameters> {
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Parameters extends ActionParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("The SFTP server host name")
        private String host;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The SFTP server port")
        private int port;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The user name")
        private String username;

        @JsonPropertyDescription("The password. If not set, will use the private key from a configured keystore.")
        private String password;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The directory to poll")
        private String directory;

        @JsonProperty(required = true)
        @JsonPropertyDescription("A regular expression that files must match to be ingressed")
        private String fileRegex;
    }

    private final SslProperties sslProperties;

    public SftpTimedIngressAction(SslProperties sslProperties) {
        super("Poll an SFTP server for files to ingress");
        this.sslProperties = sslProperties;
    }

    @Override
    public IngressResultType ingress(@NotNull ActionContext context, @NotNull SftpTimedIngressAction.Parameters params) {
        IngressResult ingressResult = new IngressResult(context);

        JSch jSch = new JSch();
        try {
            Session session = jSch.getSession(params.getUsername(), params.getHost(), params.getPort());
            session.setConfig("StrictHostKeyChecking", "no");
            if (params.getPassword() != null) {
                session.setPassword(params.getPassword());
            } else if ((sslProperties.getKeyStore() != null) && (sslProperties.getKeyStorePassword() != null)) {
                // Use private key extracted from keystore provided in ssl properties
                jSch.addIdentity(params.getUsername(), getPrivateKey(), null, null);
            } else {
                ingressResult.setStatus(IngressStatus.UNHEALTHY);
                ingressResult.setStatusMessage("Password variable not set and no keystore is configured");
                return ingressResult;
            }
            session.connect();
            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            Vector<ChannelSftp.LsEntry> lsEntries = new Vector<>();

            channel.cd(params.getDirectory());
            channel.ls(params.getDirectory(), entry -> {
                if (entry.getAttrs().isReg() && entry.getFilename().matches(params.fileRegex)) {
                    lsEntries.add(entry);
                }
                return ChannelSftp.LsEntrySelector.CONTINUE;
            });

            for (ChannelSftp.LsEntry lsEntry : lsEntries) {
                IngressResultItem resultItem = new IngressResultItem(context, lsEntry.getFilename());
                resultItem.saveContent(channel.get(lsEntry.getFilename()), lsEntry.getFilename(),
                        MediaType.MEDIA_TYPE_WILDCARD);
                ingressResult.addItem(resultItem);
                channel.rm(lsEntry.getFilename());
            }
        } catch (CertificateException | IllegalArgumentException | IOException | JSchException | KeyStoreException |
                NoSuchAlgorithmException | SftpException | UnrecoverableKeyException e) {
            ingressResult.setStatus(IngressStatus.UNHEALTHY);
            ingressResult.setStatusMessage("Unable to get files from SFTP server: " + e.getMessage());
            log.error("Unable to get files from SFTP server", e);
        }

        return ingressResult;
    }

    private byte[] getPrivateKey() throws IOException, KeyStoreException, UnrecoverableKeyException,
            NoSuchAlgorithmException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(new File(sslProperties.getKeyStore()),
                sslProperties.getKeyStorePassword().toCharArray());
        Key privateKey = keyStore.getKey(keyStore.aliases().nextElement(),
                sslProperties.getKeyStorePassword().toCharArray());

        StringWriter stringWriter = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter);
        pemWriter.writeObject(privateKey);
        pemWriter.close();
        return stringWriter.toString().getBytes();
    }
}
