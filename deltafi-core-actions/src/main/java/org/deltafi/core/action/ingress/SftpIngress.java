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
import org.deltafi.common.ssl.SslContextProvider;
import org.deltafi.common.ssl.SslContextProvider.SslException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.IngressStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.StringWriter;
import java.security.Key;
import java.util.Vector;

@Component
@Slf4j
public class SftpIngress extends TimedIngressAction<SftpIngress.Parameters> {
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

    private final JSch jSch;
    private final SslContextProvider sslContextProvider;

    public SftpIngress(JSch jSch, SslContextProvider sslContextProvider) {
        super("Poll an SFTP server for files to ingress");
        this.jSch = jSch;
        this.sslContextProvider = sslContextProvider;
    }

    @Override
    public IngressResultType ingress(@NotNull ActionContext context, @NotNull SftpIngress.Parameters params) {
        IngressResult ingressResult = new IngressResult(context);

        try {
            Session session = jSch.getSession(params.getUsername(), params.getHost(), params.getPort());
            session.setConfig("StrictHostKeyChecking", "no");
            if (params.getPassword() != null) {
                session.setPassword(params.getPassword());
            } else if (sslContextProvider.isConfigured()) {
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
        } catch (IllegalArgumentException | IOException | JSchException | SftpException | SslException e) {
            ingressResult.setStatus(IngressStatus.UNHEALTHY);
            ingressResult.setStatusMessage("Unable to get files from SFTP server: " + e.getMessage());
            log.error("Unable to get files from SFTP server", e);
        }

        return ingressResult;
    }

    private byte[] getPrivateKey() throws IOException, SslException {
        Key privateKey = sslContextProvider.getPrivateKey();
        StringWriter stringWriter = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter);
        pemWriter.writeObject(privateKey);
        pemWriter.close();
        return stringWriter.toString().getBytes();
    }
}
