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
package org.deltafi.core.rest;

import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.exceptions.InvalidEntityException;
import org.deltafi.core.types.ErrorHolder;
import org.deltafi.core.services.RoleService;
import org.deltafi.core.types.Role;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RolesRest.class)
@AutoConfigureMockMvc(addFilters = false)
class RolesRestTest {

    private static final UUID ID = new UUID(1, 0);
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2020-01-01T00:00:00.000Z");
    private static final Role ADMIN = role("admin", "Admin");
    private static final Role READ_ONLY = role("readOnly", "ReadData");
    private static final String READ_ONLY_JSON = """
                {
                  "id": "00000000-0000-0001-0000-000000000000",
                  "name": "readOnly",
                  "permissions": [
                    "ReadData"
                  ],
                  "createdAt": "2020-01-01T00:00:00Z",
                  "updatedAt": "2020-01-01T00:00:00Z"
                }
                """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleService roleService;

    @MockBean
    private CoreAuditLogger auditLogger;

    @Test
    void getRoles() throws Exception {
        Mockito.when(roleService.getRoles()).thenReturn(List.of(ADMIN, READ_ONLY));

        String json = """
                [
                  {
                    "id": "00000000-0000-0001-0000-000000000000",
                    "name": "admin",
                    "permissions": [
                      "Admin"
                    ],
                    "createdAt": "2020-01-01T00:00:00Z",
                    "updatedAt": "2020-01-01T00:00:00Z"
                  },
                """ + READ_ONLY_JSON + "]";
        mockMvc.perform(get("/api/v2/roles"))
                .andExpect(status().isOk())
                .andExpect(content().json(json));
    }

    @Test
    void getRole() throws Exception {
        Mockito.when(roleService.getRole(ID)).thenReturn(READ_ONLY);

        mockMvc.perform(get("/api/v2/roles/00000000-0000-0001-0000-000000000000"))
                .andExpect(status().isOk())
                .andExpect(content().json(READ_ONLY_JSON));
    }

    @Test
    void getRoleNotFound() throws Exception {
        Mockito.when(roleService.getRole(ID)).thenThrow(new EntityNotFound("Role with ID unknown not found."));

        mockMvc.perform(get("/api/v2/roles/00000000-0000-0001-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRole() throws Exception {
        Role.Input roleInput = new Role.Input("readOnly", List.of("ReadData"));
        Mockito.when(roleService.createRole(roleInput)).thenReturn(READ_ONLY);

        String body = """
                {
                  "name": "readOnly",
                  "permissions": ["ReadData"]
                }
                """;

        mockMvc.perform(post("/api/v2/roles").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(READ_ONLY_JSON));

        verifyAuditCalled();
    }

    @Test
    void createInvalidRole() throws Exception {
        Role.Input roleInput = new Role.Input(null, List.of("ReadData"));

        ErrorHolder errorHolder = new ErrorHolder();
        errorHolder.add("name", "Missing name");
        errorHolder.add("permissions", "Unknown permission");
        Mockito.when(roleService.createRole(roleInput)).thenThrow(new InvalidEntityException(errorHolder));

        String body = """
                {
                  "permissions": ["ReadData"]
                }
                """;

        String errors = """
                {
                  "validationErrors": {
                    "name": ["Missing name"],
                    "permissions": ["Unknown permission"]
                  }
                }
                """;
        mockMvc.perform(post("/api/v2/roles").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errors));
    }

    @Test
    void updateRole() throws Exception {
        Role.Input roleInput = new Role.Input(null, List.of("ReadData", "ViewMeta"));
        Mockito.when(roleService.updateRole(ID, roleInput)).thenReturn(READ_ONLY);

        String body = """
                {
                  "permissions": ["ReadData", "ViewMeta"]
                }
                """;

        mockMvc.perform(put("/api/v2/roles/00000000-0000-0001-0000-000000000000").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(READ_ONLY_JSON));

        assertThat(Mockito.mockingDetails(auditLogger).getInvocations().size()).isNotZero();
    }

    @Test
    void invalidRoleUpdatesUnknownField() throws Exception {
        String body = """
                {
                  "nane": "ReadAndViewOnly"
                }
                """;

        mockMvc.perform(put("/api/v2/roles/00000000-0000-0001-0000-000000000000").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field 'nane'"));
    }

    @Test
    void updateRoleNotFound() throws Exception {
        Role.Input roleInput = new Role.Input(null, List.of("ReadData", "ViewMeta"));
        Mockito.when(roleService.updateRole(ID, roleInput)).thenThrow(new EntityNotFound("Role with ID readOnly not found."));

        String body = """
                {
                  "permissions": ["ReadData", "ViewMeta"]
                }
                """;

        mockMvc.perform(put("/api/v2/roles/00000000-0000-0001-0000-000000000000").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }


    @Test
    void deleteRole() throws Exception {
        Mockito.when(roleService.deleteRole(ID)).thenReturn(READ_ONLY);

        mockMvc.perform(delete("/api/v2/roles/00000000-0000-0001-0000-000000000000"))
                .andExpect(status().isOk())
                .andExpect(content().json(READ_ONLY_JSON));

        assertThat(Mockito.mockingDetails(auditLogger).getInvocations().size()).isNotZero();
    }

    @Test
    void forbidAdminRoleDelete() throws Exception {
        mockMvc.perform(delete("/api/v2/roles/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Unable to delete the admin role."));
    }

    private void verifyAuditCalled() {
        assertThat(Mockito.mockingDetails(auditLogger).getInvocations().size()).isNotZero();
    }

    private static Role role(String name, String ... permissions) {
        return Role.builder()
                .id(ID)
                .name(name)
                .permissions(List.of(permissions))
                .createdAt(TIME)
                .updatedAt(TIME)
                .build();
    }
}