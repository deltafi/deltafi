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
package org.deltafi.core.services;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
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
public class DeltaFiUserService {

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

    @PostConstruct
    public void init() {
        deltaFiUserRepo.createAdmin(ADMIN_ID, OffsetDateTime.now());
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

        // not a direct call and the user does not exist
        if (user == null) {
            String field = authProperties.certMode() ? DN : USERNAME;
            throw new UsernameNotFoundException("No user exists with a " + field + " of " + username);
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
