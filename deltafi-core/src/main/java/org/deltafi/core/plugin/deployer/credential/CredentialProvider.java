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

import org.deltafi.core.types.Result;

/**
 * The {@link CredentialProvider} reads basic credentials
 * from a source
 */
public interface CredentialProvider {
    /**
     * Get the {@link BasicCredentials} from the given source
     * @param sourceName name of basic credentials source
     * @return object holding the username and password
     */
    BasicCredentials getCredentials(String sourceName);

    /**
     * Create basic credentials
     * @param sourceName name used to reference the credentials
     * @param username to use
     * @param password to use
     * @return result of the operation
     */
    Result createCredentials(String sourceName, String username, String password);
}
