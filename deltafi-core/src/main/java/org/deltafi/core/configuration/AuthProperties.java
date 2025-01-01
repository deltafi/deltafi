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
package org.deltafi.core.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@Data
@ConfigurationProperties("auth")
public class AuthProperties {
    public static final String DISABLED = "disabled";
    public static final String CERT = "cert";
    public static final String BASIC = "basic";
    private String mode;

    public AuthProperties(String mode) {
        setMode(mode);
    }

    public void setMode(String mode) {
        if (mode != null && !Set.of(BASIC, CERT, DISABLED).contains(mode)) {
            throw new IllegalArgumentException("Invalid mode: " + mode + " must be one of basic, cert, or disabled");
        }
        this.mode = mode != null ? mode : DISABLED;
    }

    public boolean noAuth() {
        return DISABLED.equals(mode);
    }

    public boolean certMode() {
        return CERT.equals(mode);
    }

    public boolean basicMode() {
        return BASIC.equals(mode);
    }
}
