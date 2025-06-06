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
package org.deltafi.core.services;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionsServiceTest {

    PermissionsService permissionsService;

    private PermissionsServiceTest() throws IOException {
        this.permissionsService = new PermissionsService(new ClassPathResource("permissions.csv"));
    }

    @Test
    void getPermissions() {
        assertThat(permissionsService.getPermissions()).isNotEmpty();
    }

    @Test
    void validatePermissions() {
        assertThat(permissionsService.validatePermission(List.of("Admin", "UIAccess", "StatusView"))).isTrue();
        assertThat(permissionsService.validatePermission(List.of("Admin", "Unknown", "StatusView"))).isFalse();
    }
}