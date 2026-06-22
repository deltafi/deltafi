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
package org.deltafi.core.rest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.InvalidRequestException;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.DeltaFiUserService;
import org.deltafi.core.types.*;
import org.deltafi.core.types.DeltaFiUser.Input;
import org.deltafi.core.util.UpdateMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

import static org.deltafi.common.constant.DeltaFiConstants.ADMIN_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@RestController
@RequestMapping(value = "/api/v2/users", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class UserRest {

    private final DeltaFiUserService userService;
    private final CoreAuditLogger auditLogger;

    @GetMapping
    @NeedsPermission.UserRead
    public List<DeltaFiUserDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @GetMapping(value = "/exportable", produces = MediaType.APPLICATION_JSON)
    @NeedsPermission.UserRead
    public ResponseEntity<String> exportUsersAndRoles() throws JacksonException {
        return ResponseEntity.ok(OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(userService.exportUsersAndRoles()));
    }

    @PostMapping(value = "/importable", consumes = MediaType.APPLICATION_JSON, produces = TEXT_PLAIN_VALUE)
    @NeedsPermission.UserCreate
    public ResponseEntity<String> importUsersAndRoles(@RequestBody UsersAndRoles usersAndRoles) {
        try {
            userService.importUsersAndRoles(usersAndRoles);
        } catch (Exception e) {
            return ResponseEntity.unprocessableContent().body(e.getMessage());
        }
        auditLogger.audit("Imported {} users and {} roles", usersAndRoles.users().size(), usersAndRoles.roles().size());
        return ResponseEntity.ok(null);
    }

    @GetMapping("/{id}")
    @NeedsPermission.UserRead
    public DeltaFiUserDTO getUserById(@PathVariable("id") UUID id) {
        return userService.getUserById(id);
    }

    @PostMapping
    @NeedsPermission.UserCreate
    public DeltaFiUserDTO createUser(@RequestBody DeltaFiUser.Input userInput) {
        DeltaFiUserDTO user = userService.createUser(userInput);
        auditLogger.audit("Created user {}", user.username());
        return user;
    }

    @PutMapping(value = "/{id}")
    @NeedsPermission.UserUpdate
    public DeltaFiUserDTO updateUser(@PathVariable("id") UUID id, @RequestBody String updates) {
        Input userInput = UpdateMapper.readValue(updates, Input.class);
        DeltaFiUserDTO user = userService.updateUser(id, userInput);
        auditLogger.audit("Updated user {}", user.username());
        return user;
    }

    @DeleteMapping("/{id}")
    @NeedsPermission.UserDelete
    public DeltaFiUserDTO deleteUserById(@PathVariable("id") UUID id) {
        if (ADMIN_ID.equals(id)) {
            throw new InvalidRequestException(HttpStatus.FORBIDDEN, "Unable to delete admin user.");
        }

        DeltaFiUserDTO user = userService.deleteUser(id);
        auditLogger.audit("Deleted user {}",  user.username());
        return user;
    }
}
