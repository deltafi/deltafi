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
package org.deltafi.core.action.activemq;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.parameters.EnvVar;
import org.deltafi.core.action.egress.NoContentPolicy;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ActiveMQPublishParameters extends ActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL of the JMS broker")
    private String brokerUrl;

    @JsonPropertyDescription("The user name credential (optional)")
    private String userName;

    @JsonPropertyDescription("The password credential (optional)")
    private EnvVar password;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The name of the ActiveMQ/JMS destination")
    private String destinationName;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Destination Type: QUEUE or TOPIC")
    private DestinationType destinationType;

    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("Publish with the PERSISTENT delivery mode, otherwise NON_PERSISTENT")
    private boolean persistent = true;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The JMS message body type: BYTES or TEXT")
    private MessageBodyType messageBodyType;

    @JsonPropertyDescription("List of regex patterns matching metadata keys to include in message properties. If empty, no properties are included.")
    private List<String> propertyMetadataPatterns;

    @JsonProperty(defaultValue = "ERROR")
    @JsonPropertyDescription("Policy for handling null content: FILTER (return FilterResult), ERROR (return ErrorResult), or SEND_EMPTY (continue with no content)")
    private NoContentPolicy noContentPolicy = NoContentPolicy.ERROR;

    String connectDetails() {
        String buffer = brokerUrl;
        if (userName != null && !userName.isEmpty()) {
            buffer += " user: " + userName;
        }
        if (password != null && password.getName() != null) {
            buffer += " password: " + password.getName();
        }
        return buffer;
    }

    String publishDetails() {
        return "%s %s: %s".formatted(brokerUrl, destinationType, destinationName);
    }
}
