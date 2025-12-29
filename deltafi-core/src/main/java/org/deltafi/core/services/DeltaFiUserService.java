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

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.configuration.AuthProperties;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.exceptions.InvalidEntityException;
import org.deltafi.core.repo.DeltaFiUserRepo;
import org.deltafi.core.security.DnUtil;
import org.deltafi.core.types.*;
import org.deltafi.core.types.DeltaFiUser.Input;
import org.deltafi.core.types.snapshot.RoleSnapshot;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.UserSnapshot;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.ADMIN_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeltaFiUserService implements Snapshotter {

    private static final Set<String> DEFAULT_ADMIN = Set.of(DeltaFiConstants.ADMIN_PERMISSION);
    public static final String MUST_BE_UNIQUE = "must be unique";
    public static final String USERNAME = "username";
    public static final String DN = "dn";
    public static final String NAME = "name";

    private final DeltaFiUserRepo deltaFiUserRepo;
    private final EntityResolver entityResolver;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final PermissionsService permissionsService;

    @PostConstruct
    public void init() {
        if (roleService.isScheduledMaintenance()) {
            deltaFiUserRepo.createAdmin(ADMIN_ID, OffsetDateTime.now());
        }
    }

    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Set<String> authorities = grantedAuthoritiesFromRequest();
        DeltaFiUser user = authProperties.certMode() ?
                deltaFiUserRepo.findByDn(username).orElse(null) :
                deltaFiUserRepo.findByUsername(username).orElse(null);

        // call came directly to the service bypassing auth
        if (authorities != null && user == null) {
            user = new DeltaFiUser();
            user.setId(null);
            user.setUsername(username);
        }

        if (user == null) {
            if (authProperties.basicMode()) {
                // authentication will always fail in basic anyway so short circuit to avoid extra lookups
                throw new UsernameNotFoundException("No user exists with a username of " + username);
            }
            // create an empty user that can be passed to the entity resolver
            user = emptyUser(username);
        }

        // if the authorities were not in the permissions header of the request look them up
        if (authorities == null) {
            authorities = authProperties.noAuth() ? DEFAULT_ADMIN : lookupGrantedAuthorities(user);
        }

        return  DeltaFiUserDetails.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .permissionSet(authorities)
                .build();
    }

    private DeltaFiUser emptyUser(String identifier) {
        DeltaFiUser user = new DeltaFiUser();
        user.setUsername(identifier);

        if (authProperties.certMode()) {
            user.setDn(identifier);
            try {
                user.setUsername(DnUtil.extractCommonName(identifier));
            } catch (Exception e) {
                // ignore this just use the DN as the username
            }
        }
        return user;
    }

    public List<DeltaFiUserDTO> getAllUsers() {
        return deltaFiUserRepo.findAll().stream()
                .map(DeltaFiUserDTO::new)
                .toList();
    }

    public DeltaFiUserDTO getUserById(UUID userId) {
        return new DeltaFiUserDTO(getUser(userId));
    }

    public DeltaFiUserDTO createUser(DeltaFiUser.Input userInput) {
        DeltaFiUser user = new DeltaFiUser(userInput);
        user.setRoles(roleService.getRoles(userInput.roleIds()));
        validateAndNormalizeDn(user);

        if (user.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        return new DeltaFiUserDTO(deltaFiUserRepo.save(user));
    }

    public DeltaFiUserDTO deleteUser(UUID userId) {
        DeltaFiUserDTO user = getUserById(userId);

        deltaFiUserRepo.deleteById(user.id());
        return user;
    }

    public DeltaFiUserDTO updateUser(UUID id, Input input) {
        DeltaFiUser user = getUser(id);
        if (input == null || input.noChanges()) {
            return new DeltaFiUserDTO(user);
        }
        input.update(user, passwordEncoder);

        if (input.roleIds() != null) {
            user.setRoles(roleService.getRoles(input.roleIds()));
        }

        validateAndNormalizeDn(user);
        return new DeltaFiUserDTO(deltaFiUserRepo.save(user));
    }

    public static boolean currentUserCanViewMasked() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        return authentication.getAuthorities().stream().anyMatch(DeltaFiUserService::hasPluginVariableUpdate);
    }

    /**
     * Update the Snapshot with current system state
     *
     * @param snapshot system snapshot that holds the current system state
     */
    @Override
    public void updateSnapshot(Snapshot snapshot) {
        snapshot.setUsers(deltaFiUserRepo.findAll().stream().map(UserSnapshot::new).toList());
        snapshot.setRoles(roleService.getRoles().stream().map(RoleSnapshot::new).toList());
    }

    /**
     * Reset the system to the state in the Snapshot
     *
     * @param snapshot system snapshot that holds the state at the time of the snapshot
     * @param hardReset      when true reset all other custom settings before applying the system snapshot values
     * @return the Result of the reset that will hold any errors or information about the reset
     */
    @Override
    @Transactional
    public Result resetFromSnapshot(Snapshot snapshot, boolean hardReset) {
        if (isEmpty(snapshot.getUsers()) && isEmpty(snapshot.getRoles())) {
            return Result.builder().info(List.of("Skipped missing users and roles section")).build();
        }
        removeInvalidPermissions(snapshot);
        return hardReset ? hardReset(snapshot) : softReset(snapshot);
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private void removeInvalidPermissions(Snapshot snapshot) {
        for (RoleSnapshot roleSnapshot : snapshot.getRoles()) {
            roleSnapshot.setPermissions(permissionsService.filterValidPermissions(roleSnapshot.getPermissions()));
        }

        for (UserSnapshot userSnapshot : snapshot.getUsers()) {
            for (RoleSnapshot roleSnapshot : userSnapshot.getRoles()) {
                roleSnapshot.setPermissions(permissionsService.filterValidPermissions(roleSnapshot.getPermissions()));
            }
        }
    }

    private Result hardReset(Snapshot snapshot) {
        deltaFiUserRepo.deleteAll();
        deltaFiUserRepo.flush();
        roleService.deleteAllRoles();
        roleService.saveAll(getSnapshotRoles(snapshot));
        deltaFiUserRepo.saveAll(getSnapshotUsers(snapshot));
        return new Result();
    }

    private Result softReset(Snapshot snapshot) {
        Map<String, Role> existingRolesByName = roleService.getRoles().stream()
                .collect(Collectors.toMap(Role::getName, r -> r));

        List<Role> snapshotRoles = getSnapshotRoles(snapshot);
        List<DeltaFiUser> snapshotUsers = getSnapshotUsers(snapshot);

        List<Role> rolesToSave = new ArrayList<>();
        for (Role snapshotRole : snapshotRoles) {
            Role role = existingRolesByName.computeIfAbsent(snapshotRole.getName(), Role::new);
            mergeRole(snapshotRole, role);
            rolesToSave.add(role);
        }
        roleService.saveAll(rolesToSave);

        Map<String, DeltaFiUser> existingUsersByUsername = deltaFiUserRepo.findAll().stream()
                .collect(Collectors.toMap(DeltaFiUser::getUsername, u -> u));

        List<DeltaFiUser> usersToSave = new ArrayList<>();
        for (DeltaFiUser snapshotUser : snapshotUsers) {
            DeltaFiUser user = existingUsersByUsername.computeIfAbsent(snapshotUser.getUsername(), DeltaFiUser::new);
            mergeUser(snapshotUser, user, existingRolesByName);
            usersToSave.add(user);
        }
        deltaFiUserRepo.saveAll(usersToSave);

        return new Result();
    }

    private void mergeRole(Role source, Role target) {
        target.setName(source.getName());
        target.setPermissions(source.getPermissions());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private void mergeUser(DeltaFiUser source, DeltaFiUser target, Map<String, Role> rolesByName) {
        target.setName(source.getName());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setUsername(source.getUsername());
        target.setPassword(source.getPassword());
        target.setDn(source.getDn());

        target.getRoles().clear();
        for (Role sourceRole : source.getRoles()) {
            Role resolvedRole = rolesByName.get(sourceRole.getName());
            if (resolvedRole != null) {
                target.getRoles().add(resolvedRole);
            }
        }
    }

    private List<DeltaFiUser> getSnapshotUsers(Snapshot snapshot) {
        return snapshot.getUsers().stream().map(UserSnapshot::toDeltaFiUser).toList();
    }

    private List<Role> getSnapshotRoles(Snapshot snapshot) {
        return snapshot.getRoles().stream().map(RoleSnapshot::toRole).toList();
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.USER_ROLE_ORDER;
    }

    private Set<String> grantedAuthoritiesFromRequest() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String permissions = request.getHeader(DeltaFiConstants.PERMISSIONS_HEADER);
        return permissions != null ?
                Arrays.stream(org.springframework.util.StringUtils.tokenizeToStringArray(permissions, ",")).collect(Collectors.toSet()) : null;
    }

    private Set<String> lookupGrantedAuthorities(DeltaFiUser user) {
        Set<String> authorities = allRolesForUser(user).stream()
                .map(Role::getPermissions)
                .filter(Objects::nonNull).flatMap(Collection::stream)
                .filter(Objects::nonNull).map(String::trim)
                .collect(Collectors.toSet());

        log.trace("Loader user {} and authorities: {}", user, authorities);
        return authorities;
    }

    private Set<Role> allRolesForUser(DeltaFiUser user) {
        String searchBy = authProperties.certMode() ? user.getDn() : user.getUsername();
        Set<String> identities = entityResolver.resolve(searchBy);
        // if there is only one identity skip the query and use the user
        List<DeltaFiUser> users = identities.size() > 1 ?
                findByIdentities(identities) : List.of(user);

        return users.stream()
                .map(DeltaFiUser::getRoles)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    public List<DeltaFiUser> findByIdentities(Collection<String> identities) {
        return authProperties.certMode() ? deltaFiUserRepo.findByDnIn(identities) : deltaFiUserRepo.findByUsernameIn(identities);
    }

    private static boolean hasPluginVariableUpdate(GrantedAuthority grantedAuthority) {
        String auth = grantedAuthority != null ? grantedAuthority.getAuthority() : null;
        return DeltaFiConstants.ADMIN_PERMISSION.equals(auth) || "PluginVariableUpdate".equals(auth);
    }

    public static String currentUsername() {
        SecurityContext securityContext = SecurityContextHolder.getContext();

        if (securityContext == null || securityContext.getAuthentication() == null || securityContext.getAuthentication().getPrincipal() == null) {
            return null;
        }

        Object principal = securityContext.getAuthentication().getPrincipal();

        if (principal instanceof UserDetails user) {
            return user.getUsername();
        }

        return principal.toString();
    }

    private DeltaFiUser getUser(UUID userId) {
        return deltaFiUserRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFound("User with ID " + userId + " not found."));
    }

    void validateAndNormalizeDn(DeltaFiUser user) {
        ErrorHolder errors = new ErrorHolder();
        String dn = user.getDn();
        if (StringUtils.isAllBlank(dn, user.getUsername())) {
            errors.add(DN, "DN or username is required");
            errors.add(USERNAME, "DN or username is required");
        }

        if (dn != null) {
            try {
                String normalizedDn = DnUtil.normalizeDn(dn);
                user.setDn(normalizedDn);
                user.setUsername(DnUtil.extractCommonName(normalizedDn));
            } catch (Exception e) {
                errors.add(DN, "must be a valid Distinguished Name");
            }
        }

        if (StringUtils.isBlank(user.getName())) {
            errors.add(NAME,"cannot be empty");
        }

        if (user.getId() != null) {
            uniqueUpdateChecks(user, errors);
        } else {
            uniqueCreateChecks(user, errors);
        }

        if (errors.notEmpty()) {
            throw new InvalidEntityException(errors);
        }
    }

    private void uniqueCreateChecks(DeltaFiUser user, ErrorHolder errors) {
        if (deltaFiUserRepo.existsByName(user.getName())) {
            errors.add(NAME, MUST_BE_UNIQUE);
        }

        if (deltaFiUserRepo.existsByUsername(user.getUsername())) {
            errors.add(USERNAME, MUST_BE_UNIQUE);
        }

        if (user.getDn() != null && deltaFiUserRepo.existsByDn(user.getDn())) {
            errors.add(DN, MUST_BE_UNIQUE);
        }
    }

    private void uniqueUpdateChecks(DeltaFiUser user, ErrorHolder errors) {
        if (deltaFiUserRepo.existsByIdNotAndName(user.getId(),user.getName())) {
            errors.add(NAME, MUST_BE_UNIQUE);
        }

        if (deltaFiUserRepo.existsByIdNotAndUsername(user.getId(), user.getUsername())) {
            errors.add(USERNAME, MUST_BE_UNIQUE);
        }

        if (user.getDn() != null && deltaFiUserRepo.existsByIdNotAndDn(user.getId(), user.getDn())) {
            errors.add(DN, MUST_BE_UNIQUE);
        }
    }
}
