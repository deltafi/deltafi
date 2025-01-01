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

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PermissionsService {

    @Getter
    private final List<Permission> permissions;
    private final Set<String> permissionNames;

    public PermissionsService(@Value("classpath:permissions.csv") Resource resource) throws IOException {
        permissions = new ArrayList<>();
        permissionNames = new HashSet<>();

        // read the permissions file, skipping the header row
        List<String> lines = resource.getContentAsString(StandardCharsets.UTF_8).lines().skip(1).toList();
        for (String line : lines) {
            Permission permission = Permission.permission(line);
            permissions.add(permission);
            permissionNames.add(permission.name());
        }
    }

    public boolean validatePermission(List<String> permissions) {
        return permissions == null || permissionNames.containsAll(permissions);
    }

    public List<String> filterValidPermissions(List<String> permissions) {
        return permissions.stream().filter(permissionNames::contains).toList();
    }

    public record Permission(String category, String name, String description) {
        public static Permission permission(String line) {
            String[] row = line.split(",");
            if (!(row.length == 3 && row[1].matches("^[a-zA-Z]*$"))) {
                throw new IllegalArgumentException("Invalid permission: " + line);
            }
            return new Permission(row[0], row[1], row[2]);
        }
    }
}
