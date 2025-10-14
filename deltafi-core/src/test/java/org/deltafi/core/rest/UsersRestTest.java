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
package org.deltafi.core.rest;

import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.exceptions.InvalidEntityException;
import org.deltafi.core.types.DeltaFiUser.Input;
import org.deltafi.core.types.ErrorHolder;
import org.deltafi.core.services.DeltaFiUserService;
import org.deltafi.core.types.DeltaFiUser;
import org.deltafi.core.types.DeltaFiUserDTO;
import org.deltafi.core.types.Role;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserRest.class)
@AutoConfigureMockMvc(addFilters = false)
class UsersRestTest {
    private static final UUID ID = new UUID(1L, 0L);
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2020-01-01T00:00:00.000Z");
    private static final Role ADMIN = role("admin", "Admin");
    private static final Role READ_ONLY = role("readOnly", "ReadData");
    private static final DeltaFiUserDTO ADMIN_USER = new DeltaFiUserDTO(user("admin", ADMIN));
    private static final DeltaFiUserDTO SIMPLE_USER = new DeltaFiUserDTO(user("simple", READ_ONLY));

    private static final String SIMPLE_USER_JSON = """
            {
                  "id": "00000000-0000-0001-0000-000000000000",
                  "name": "simple",
                  "dn": null,
                  "username": "simple",
                  "createdAt": "2020-01-01T00:00:00Z",
                  "updatedAt": "2020-01-01T00:00:00Z",
                  "roles": [
                    {
                      "name": "readOnly",
                      "permissions": [
                        "ReadData"
                      ],
                      "createdAt": "2020-01-01T00:00:00Z",
                      "updatedAt": "2020-01-01T00:00:00Z"
                    }
                  ],
                  "permissions": [
                    "ReadData"
                  ]
                }""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeltaFiUserService userService;

    @MockitoBean
    private CoreAuditLogger auditLogger;

    @Test
    void getUsers() throws Exception {
        Mockito.when(userService.getAllUsers()).thenReturn(List.of(ADMIN_USER, SIMPLE_USER));

        String json = """
                [
                  {
                      "id": "00000000-0000-0001-0000-000000000000",
                      "name": "admin",
                      "dn": null,
                      "username": "admin",
                      "createdAt": "2020-01-01T00:00:00Z",
                      "updatedAt": "2020-01-01T00:00:00Z",
                      "roles": [
                        {
                          "name": "admin",
                          "permissions": [
                            "Admin"
                          ],
                          "createdAt": "2020-01-01T00:00:00Z",
                          "updatedAt": "2020-01-01T00:00:00Z"
                        }
                      ],
                      "permissions": [
                        "Admin"
                      ]
                    },
                """ + SIMPLE_USER_JSON + "]";
        mockMvc.perform(get("/api/v2/users"))
                .andExpect(status().isOk())
                .andExpect(content().json(json));
    }

    @Test
    void getUser() throws Exception {
        Mockito.when(userService.getUserById(ID)).thenReturn(SIMPLE_USER);

        mockMvc.perform(get("/api/v2/users/00000000-0000-0001-0000-000000000000"))
                .andExpect(status().isOk())
                .andExpect(content().json(SIMPLE_USER_JSON));
    }

    @Test
    void getUserNotFound() throws Exception {
        UUID uuid = UUID.randomUUID();
        Mockito.when(userService.getUserById(uuid)).thenThrow(new EntityNotFound("User with ID unknown not found."));

        mockMvc.perform(get("/api/v2/users/" + uuid))
                .andExpect(status().isNotFound());
    }

    @Test
    void createUser() throws Exception {
        DeltaFiUser.Input simple = new DeltaFiUser.Input("simple", "simple", null, "simple", null);

        Mockito.when(userService.createUser(simple)).thenReturn(SIMPLE_USER);

        String body = """
                {
                  "name": "simple",
                  "username": "simple",
                  "password": "simple"
                }
                """;

        mockMvc.perform(post("/api/v2/users").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(SIMPLE_USER_JSON));

        verifyAuditCalled();
    }

    @Test
    void createInvalidUser() throws Exception {
        DeltaFiUser.Input bob = new DeltaFiUser.Input("bob", null, "some dn", null, null);
        ErrorHolder errorHolder = new ErrorHolder();
        errorHolder.add("password", "Missing password");
        errorHolder.add("dn", "Invalid dn");
        Mockito.when(userService.createUser(bob)).thenThrow(new InvalidEntityException(errorHolder));

        String body = """
                {
                  "name": "bob",
                  "dn": "some dn"
                }
                """;

        String errors = """
                {
                  "validationErrors": {
                    "password": ["Missing password"],
                    "dn": ["Invalid dn"]
                  }
                }
                """;
        mockMvc.perform(post("/api/v2/users").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errors));
    }

    @Test
    void updateUser() throws Exception {
        Input input = new Input(null, null, null, "newPassword", null);
        Mockito.when(userService.updateUser(ID, input)).thenReturn(SIMPLE_USER);

        String body = """
                {
                  "password": "newPassword"
                }
                """;

        mockMvc.perform(put("/api/v2/users/00000000-0000-0001-0000-000000000000").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(SIMPLE_USER_JSON));

        assertThat(Mockito.mockingDetails(auditLogger).getInvocations().size()).isNotZero();
    }

    @Test
    void invalidUserUpdatesUnknownField() throws Exception {
        String body = """
                {
                  "nane": "ReadAndViewOnly"
                }
                """;
        mockMvc.perform(put("/api/v2/users/00000000-0000-0001-0000-000000000000").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field 'nane'"));
    }

    @Test
    void updateUserNotFound() throws Exception {
        Input input = new Input(null, null, null, "newPassword", null);
        Mockito.when(userService.updateUser(ID, input)).thenThrow(new EntityNotFound("User with ID readOnly not found."));

        String body = """
                {
                  "password": "newPassword"
                }
                """;

        mockMvc.perform(put("/api/v2/users/00000000-0000-0001-0000-000000000000").content(body).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }


    @Test
    void deleteUser() throws Exception {
        UUID uuid = UUID.randomUUID();
        Mockito.when(userService.deleteUser(uuid)).thenReturn(SIMPLE_USER);

        mockMvc.perform(delete("/api/v2/users/" + uuid))
                .andExpect(status().isOk())
                .andExpect(content().json(SIMPLE_USER_JSON));

        assertThat(Mockito.mockingDetails(auditLogger).getInvocations().size()).isNotZero();
    }

    @Test
    void forbidAdminUserDelete() throws Exception {
        mockMvc.perform(delete("/api/v2/users/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Unable to delete admin user."));
    }

    private void verifyAuditCalled() {
        assertThat(Mockito.mockingDetails(auditLogger).getInvocations().size()).isNotZero();
    }

    private static DeltaFiUser user(String name, Role role) {
        return DeltaFiUser.builder()
                .id(ID)
                .name(name)
                .username(name)
                .createdAt(TIME)
                .updatedAt(TIME)
                .password("password")
                .roles(Set.of(role))
                .build();
    }

    private static Role role(String name, String ... permissions) {
        return Role.builder()
                .id(UUID.randomUUID())
                .name(name)
                .permissions(List.of(permissions))
                .createdAt(TIME)
                .updatedAt(TIME)
                .build();
    }
}