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

import org.assertj.core.api.Assertions;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.configuration.AuthProperties;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.exceptions.InvalidEntityException;
import org.deltafi.core.repo.DeltaFiUserRepo;
import org.deltafi.core.types.*;
import org.deltafi.core.types.DeltaFiUser.Input;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class DeltaFiUserServiceTest {
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2020-01-01T00:00:00.000Z");
    public static final UUID BOB_ID = new UUID(1, 0);
    public static final String BOB_NAME = "bob";
    public static final Role roleA = Role.builder().name("a").permissions(List.of("UIView")).build();
    public static final Role roleB = Role.builder().name("b").permissions(List.of("UIView")).build();
    private static final DeltaFiUser BOB = DeltaFiUser.builder()
            .id(BOB_ID)
            .name("Bob")
            .username("bob")
            .dn("CN=Bob, OU=Foo, C=US")
            .password("password")
            .createdAt(TIME)
            .updatedAt(TIME)
            .roles(Set.of(roleA, roleB))
            .build();

    private static final DeltaFiUserDTO BOB_DTO = new DeltaFiUserDTO(BOB);

    public static final Role superRole = Role.builder().name("super").permissions(List.of("Admin")).build();
    private static final DeltaFiUser SUPER_BOB = DeltaFiUser.builder().roles(Set.of(superRole, roleB)).build();

    private final DeltaFiUserService userService;
    private final DeltaFiUserRepo deltaFiUserRepo;
    private final EntityResolver entityResolver;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    @Captor
    private ArgumentCaptor<DeltaFiUser> userCaptor;

    public DeltaFiUserServiceTest(@Mock DeltaFiUserRepo deltaFiUserRepo, @Mock EntityResolver entityResolver,
                                  @Mock RoleService roleService, @Mock PasswordEncoder passwordEncoder, @Mock PermissionsService permissionsService) {
        this.authProperties = new AuthProperties(null);
        this.userService = new DeltaFiUserService(deltaFiUserRepo, entityResolver, roleService, passwordEncoder, authProperties, permissionsService);
        this.deltaFiUserRepo = deltaFiUserRepo;
        this.entityResolver = entityResolver;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    @Test
    void loadUserByUsernameBasicMode() {
        setupRequestHolder();
        Set<String> identities = Set.of(BOB_NAME, "super-bob");
        authProperties.setMode("basic");
        Mockito.when(deltaFiUserRepo.findByUsername(BOB_NAME)).thenReturn(Optional.of(BOB));
        Mockito.when(entityResolver.resolve(BOB_NAME)).thenReturn(identities);

        Mockito.when(deltaFiUserRepo.findByUsernameIn(identities))
                        .thenReturn(List.of(BOB, SUPER_BOB));

        Set<String> expectedPermissions = Set.of("UIView", "Admin");

        DeltaFiUserDetails user = load();
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo(BOB_NAME);
        assertThat(user.getPassword()).isEqualTo("password");
        assertThat(user.getAuthorities()).isEqualTo(Set.of(new SimpleGrantedAuthority("UIView"), new SimpleGrantedAuthority("Admin")));
        assertThat(user.isAdmin()).isTrue();
        assertThat(user.getMetricsRole()).isEqualTo("Admin");
        assertThat(user.getPermissions()).contains("UIView", "Admin");
        assertThat(user.getPermissionSet()).isEqualTo(expectedPermissions);
    }

    @Test
    void loadUserByUsernameCertMode() {
        setupRequestHolder();
        Set<String> identities = Set.of(BOB_NAME, "super-bob");
        authProperties.setMode("cert");
        Mockito.when(deltaFiUserRepo.findByDn(BOB.getDn())).thenReturn(Optional.of(BOB));
        Mockito.when(entityResolver.resolve(BOB.getDn())).thenReturn(identities);
        Mockito.when(deltaFiUserRepo.findByDnIn(identities)).thenReturn(List.of(BOB, SUPER_BOB));

        Set<String> expectedPermissions = Set.of("UIView", "Admin");
        DeltaFiUserDetails user = load(BOB.getDn());

        // verify we only searched by DN in cert mode
        Mockito.verify(deltaFiUserRepo, Mockito.never()).findByUsername(BOB_NAME);

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo(BOB_NAME);
        assertThat(user.getPassword()).isEqualTo("password");
        assertThat(user.getAuthorities()).isEqualTo(Set.of(new SimpleGrantedAuthority("UIView"), new SimpleGrantedAuthority("Admin")));
        assertThat(user.isAdmin()).isTrue();
        assertThat(user.getPermissions()).contains("UIView", "Admin");
        assertThat(user.getMetricsRole()).isEqualTo("Admin");
        assertThat(user.getPermissionSet()).isEqualTo(expectedPermissions);
    }

    @Test
    void loadUserByUsernameDisabledAuth() {
        setupRequestHolder();
        authProperties.setMode("disabled");
        Mockito.when(deltaFiUserRepo.findByUsername(BOB_NAME)).thenReturn(Optional.of(BOB));

        DeltaFiUserDetails user = load();

        Mockito.verify(deltaFiUserRepo, Mockito.never()).findByUsernameIn(Mockito.anyList());
        Mockito.verify(entityResolver, Mockito.never()).resolve(Mockito.anyString());

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo(BOB_NAME);
        assertThat(user.getPassword()).isEqualTo("password");
        assertThat(user.getAuthorities()).isEqualTo(Set.of(new SimpleGrantedAuthority("Admin")));
        assertThat(user.isAdmin()).isTrue();
        assertThat(user.getPermissions()).isEqualTo("Admin");
        assertThat(user.getMetricsRole()).isEqualTo("Admin");
        assertThat(user.getPermissionSet()).isEqualTo(Set.of("Admin"));
    }

    @Test
    void loadUserByUsernameWithPermissionsHeader() {
        setupRequestHolder("UIAccess,StatusView,DashboardView");
        authProperties.setMode("basic");
        Mockito.when(deltaFiUserRepo.findByUsername(BOB_NAME)).thenReturn(Optional.of(BOB));

        DeltaFiUserDetails user = load();

        // no lookups needed because permissions are in the header
        Mockito.verify(deltaFiUserRepo, Mockito.never()).findByUsernameIn(Mockito.anyList());
        Mockito.verify(entityResolver, Mockito.never()).resolve(Mockito.anyString());

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo(BOB_NAME);
        assertThat(user.getPassword()).isEqualTo("password");
        assertThat(user.getAuthorities()).isEqualTo(Set.of(new SimpleGrantedAuthority("UIAccess"), new SimpleGrantedAuthority("StatusView"), new SimpleGrantedAuthority("DashboardView")));
        assertThat(user.isAdmin()).isFalse();
        assertThat(user.getPermissions()).contains("UIAccess", "StatusView", "DashboardView");
        assertThat(user.getMetricsRole()).isEmpty();
        assertThat(user.getPermissionSet()).isEqualTo(Set.of("UIAccess", "StatusView", "DashboardView"));
    }

    @Test
    void loadUserByUsernameBypassAuth() {
        setupRequestHolder("UIAccess,StatusView,DashboardView");
        authProperties.setMode("basic");

        Mockito.when(deltaFiUserRepo.findByUsername(BOB_NAME)).thenReturn(Optional.empty());

        DeltaFiUserDetails user = load();

        // no lookups needed because permissions are in the header
        Mockito.verify(deltaFiUserRepo, Mockito.never()).findByUsernameIn(Mockito.anyList());
        Mockito.verify(entityResolver, Mockito.never()).resolve(Mockito.anyString());

        // bob doesn't exist but when the permissions are present we use them (i.e. deltafi-cli user)
        assertThat(user).isNotNull();
        assertThat(user.getId()).isEqualTo("none");
        assertThat(user.getUsername()).isEqualTo(BOB_NAME);
        assertThat(user.getPassword()).isNull();
        assertThat(user.getAuthorities()).isEqualTo(Set.of(new SimpleGrantedAuthority("UIAccess"), new SimpleGrantedAuthority("StatusView"), new SimpleGrantedAuthority("DashboardView")));
        assertThat(user.isAdmin()).isFalse();
        assertThat(user.getPermissions()).contains("UIAccess", "StatusView", "DashboardView");
        assertThat(user.getMetricsRole()).isEmpty();
        assertThat(user.getPermissionSet()).isEqualTo(Set.of("UIAccess", "StatusView", "DashboardView"));
    }

    @Test
    void loadUserByUsernameMissingUserResolvedByEntityResolver() {
        setupRequestHolder();
        Set<String> identities = Set.of(BOB.getDn(), "super-bob");
        authProperties.setMode("cert");
        Mockito.when(deltaFiUserRepo.findByDn(BOB.getDn())).thenReturn(Optional.empty());
        Mockito.when(entityResolver.resolve(BOB.getDn())).thenReturn(identities);
        Mockito.when(deltaFiUserRepo.findByDnIn(identities)).thenReturn(List.of(SUPER_BOB));

        Set<String> expectedPermissions = Set.of("UIView", "Admin");
        DeltaFiUserDetails user = load(BOB.getDn());

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("Bob"); // extracted from the DN
        assertThat(user.getPassword()).isNull();
        assertThat(user.getAuthorities()).isEqualTo(Set.of(new SimpleGrantedAuthority("UIView"), new SimpleGrantedAuthority("Admin")));
        assertThat(user.isAdmin()).isTrue();
        assertThat(user.getPermissions()).contains("UIView", "Admin");
        assertThat(user.getMetricsRole()).isEqualTo("Admin");
        assertThat(user.getPermissionSet()).isEqualTo(expectedPermissions);
    }

    @Test
    void loadUserByUsernameMissingUserUnresolvedByEntityResolver() {
        setupRequestHolder();
        Set<String> identities = Set.of(BOB.getDn());
        authProperties.setMode("cert");
        Mockito.when(deltaFiUserRepo.findByDn(BOB.getDn())).thenReturn(Optional.empty());
        Mockito.when(entityResolver.resolve(BOB.getDn())).thenReturn(identities);
        Mockito.when(deltaFiUserRepo.findByDnIn(identities)).thenReturn(List.of());

        DeltaFiUserDetails user = load(BOB.getDn());

        // no extra lookups when the entity resolver didn't find any other identities
        Mockito.verify(deltaFiUserRepo, Mockito.never()).findByDnIn(Mockito.anySet());

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("Bob"); // extracted from the DN
        assertThat(user.getPassword()).isNull();
        assertThat(user.getAuthorities()).isEmpty();
        assertThat(user.isAdmin()).isFalse();
        assertThat(user.getPermissions()).isBlank();
        assertThat(user.getMetricsRole()).isBlank();
        assertThat(user.getPermissionSet()).isEmpty();
    }

    @Test
    void getAllUsers() {
        Mockito.when(deltaFiUserRepo.findAll()).thenReturn(List.of(BOB));
        List<DeltaFiUserDTO> users = userService.getAllUsers();
        assertThat(users).hasSize(1).contains(BOB_DTO);
    }

    @Test
    void getUserById() {
        Mockito.when(deltaFiUserRepo.findById(BOB_ID)).thenReturn(Optional.of(BOB));
        DeltaFiUserDTO user = userService.getUserById(BOB_ID);
        assertThat(user).isEqualTo(BOB_DTO);
    }

    @Test
    void getUserByIdNotFound() {
        Mockito.when(deltaFiUserRepo.findById(BOB_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getUserById(BOB_ID))
                .isInstanceOf(EntityNotFound.class)
                .hasMessage("User with ID " + BOB_ID + " not found.");
    }

    @Test
    void createUser() {
        Set<UUID> uuids = Set.of(UUID.randomUUID());
        Input input = Input.builder()
                .name(BOB_NAME)
                .username("bobster")
                .dn("CN=Bob ,OU=Foo, C=US")
                .password("password")
                .roleIds(uuids)
                .build();

        Role role = new Role();
        Mockito.when(roleService.getRoles(uuids)).thenReturn(Set.of(role));
        Mockito.when(passwordEncoder.encode("password")).thenReturn("scrambled");
        Mockito.when(deltaFiUserRepo.save(userCaptor.capture())).thenReturn(BOB);

        userService.createUser(input);

        DeltaFiUser user = userCaptor.getValue();
        assertThat(user.getPassword()).isEqualTo("scrambled");
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
        // verify the dn is normalized
        assertThat(user.getDn()).isEqualTo("CN=Bob, OU=Foo, C=US");
        // when the DN is present the username is always common name
        assertThat(user.getUsername()).isEqualTo("Bob");
        assertThat(user.getRoles()).hasSize(1).contains(role);
    }

    @Test
    void createInvalidUser() {
        Input input = Input.builder()
                .password("password")
                .roleIds(Set.of(UUID.randomUUID()))
                .build();

        ErrorHolder expected = new ErrorHolder();
        expected.add("dn", "DN or username is required");
        expected.add("username", "DN or username is required");
        expected.add("name", "cannot be empty");
        assertThatThrownBy(() -> userService.createUser(input))
                .isInstanceOf(InvalidEntityException.class)
                .extracting(e -> ((InvalidEntityException) e).getErrorHolder())
                .isEqualTo(expected);
    }

    @Test
    void deleteUser() {
        Mockito.when(deltaFiUserRepo.findById(BOB_ID)).thenReturn(Optional.of(BOB));
        DeltaFiUserDTO user = userService.deleteUser(BOB_ID);
        Mockito.verify(deltaFiUserRepo).deleteById(BOB_ID);
        assertThat(user).isEqualTo(BOB_DTO);
    }

    @Test
    void deleteUserNotFound() {
        Mockito.when(deltaFiUserRepo.findById(BOB_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.deleteUser(BOB_ID))
                .isInstanceOf(EntityNotFound.class)
                .hasMessage("User with ID " + BOB_ID + " not found.");
        Mockito.verify(deltaFiUserRepo, Mockito.never()).deleteById(BOB_ID);
    }

    @Test
    void updateUser() {
        // don't use static BOB or it breaks other tests
        DeltaFiUser toUpdate = DeltaFiUser.builder()
                .name(BOB_NAME)
                .dn("CN=Bob, OU=Foo, C=US")
                .password("password")
                .username("bob")
                .build();

        Input updates = Input.builder()
                .name("bobo")
                .dn("CN=Bobo,   OU = Foo, C=US")
                .password("betterpass")
                .build();

        Mockito.when(deltaFiUserRepo.findById(BOB_ID)).thenReturn(Optional.of(toUpdate));
        Mockito.when(deltaFiUserRepo.save(userCaptor.capture())).thenReturn(BOB);
        Mockito.when(passwordEncoder.encode("betterpass")).thenReturn("encodedbetter");
        userService.updateUser(BOB_ID, updates);

        DeltaFiUser user = userCaptor.getValue();
        assertThat(user.getName()).isEqualTo("bobo");
        assertThat(user.getUsername()).isEqualTo("Bobo");
        assertThat(user.getDn()).isEqualTo("CN=Bobo, OU=Foo, C=US");
        assertThat(user.getPassword()).isEqualTo("encodedbetter");
    }

    @Test
    void updateUserEmptyChanges() {
        Mockito.when(deltaFiUserRepo.findById(BOB_ID)).thenReturn(Optional.of(BOB));
        userService.updateUser(BOB_ID, Input.builder().build());
        Mockito.verify(deltaFiUserRepo).findById(BOB_ID);
        Mockito.verifyNoMoreInteractions(deltaFiUserRepo);
    }

    @Test
    void updateUserNullChanges() {
        Mockito.when(deltaFiUserRepo.findById(BOB_ID)).thenReturn(Optional.of(BOB));
        userService.updateUser(BOB_ID, null);
        Mockito.verify(deltaFiUserRepo).findById(BOB_ID);
        Mockito.verifyNoMoreInteractions(deltaFiUserRepo);
    }

    @Test
    void updateUserNotFound() {
        Mockito.when(deltaFiUserRepo.findById(BOB_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.updateUser(BOB_ID, null))
                .isInstanceOf(EntityNotFound.class)
                .hasMessage("User with ID " + BOB_ID + " not found.");
    }

    @Test
    void validateAndNormalizeDnBadDn() {
        DeltaFiUser user = DeltaFiUser.builder().dn("malformed dn").build();
        Map<String, List<String>> validationErrors = validateErrors(user);
        assertThat(validationErrors).containsEntry("dn", List.of("must be a valid Distinguished Name"));
    }

    @Test
    void validateAndNormalizeMultipleErrors() {
        UUID id = UUID.randomUUID();
        DeltaFiUser user = DeltaFiUser.builder().id(id).name("").username("bob").dn("malformed dn").build();
        Mockito.when(deltaFiUserRepo.existsByIdNotAndName(id, "")).thenReturn(true);
        Mockito.when(deltaFiUserRepo.existsByIdNotAndUsername(id, "bob")).thenReturn(true);
        Mockito.when(deltaFiUserRepo.existsByIdNotAndDn(id, "malformed dn")).thenReturn(true);

        Map<String, List<String>> validationErrors = validateErrors(user);
        assertThat(validationErrors)
                .hasSize(3)
                .containsEntry("dn", List.of("must be a valid Distinguished Name", "must be unique"))
                .containsEntry("name", List.of("cannot be empty", "must be unique"))
                .containsEntry("username", List.of("must be unique"));
    }

    @Test
    void currentUsername() {
        Authentication mocked = Mockito.mock(Authentication.class);
        Mockito.when(mocked.getPrincipal()).thenReturn(DeltaFiUserDetails.builder().username(BOB_NAME).build());
        SecurityContextHolder.getContext().setAuthentication(mocked);
        assertThat(DeltaFiUserService.currentUsername()).isEqualTo(BOB_NAME);
    }
    
    @Test
    void currentUsernameNoUser() {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        assertThat(DeltaFiUserService.currentUsername()).isNull();
    }

    @Test
    void canViewMaskedWithAdmin() {
        setupSecurityContext("Admin");
        assertThat(DeltaFiUserService.currentUserCanViewMasked()).isTrue();
    }

    @Test
    void canViewMaskedWithPluginVariableUpdate() {
        setupSecurityContext("PluginVariableUpdate");
        assertThat(DeltaFiUserService.currentUserCanViewMasked()).isTrue();
    }

    @Test
    void canViewMaskedDenied() {
        setupSecurityContext("UIAccess");
        assertThat(DeltaFiUserService.currentUserCanViewMasked()).isFalse();
    }

    @Test
    void canViewMaskedNoAuth() {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThat(DeltaFiUserService.currentUserCanViewMasked()).isFalse();

        Authentication mocked = Mockito.mock(Authentication.class);
        Mockito.when(mocked.getAuthorities()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(mocked);

        assertThat(DeltaFiUserService.currentUserCanViewMasked()).isFalse();
    }

    @Test
    void metricRoleMapper() {
        assertThat(metricsRole()).isEmpty();
        assertThat(metricsRole("UIAccess")).isEmpty();
        assertThat(metricsRole("Admin")).isEqualTo("Admin");
        assertThat(metricsRole("Admin", "MetricsView")).isEqualTo("Admin");
        assertThat(metricsRole("MetricsAdmin")).isEqualTo("Admin");
        assertThat(metricsRole("MetricsEdit")).isEqualTo("Editor");
        assertThat(metricsRole("MetricsView")).isEqualTo("Viewer");
    }

    private String metricsRole(String ... permissions) {
        Set<String> permissionSet = permissions != null ? Set.of(permissions) : null;
        return DeltaFiUserDetails.builder().permissionSet(permissionSet).build().metricsRole();
    }

    private DeltaFiUserDetails load() {
        return load(BOB_NAME);
    }

    private DeltaFiUserDetails load(String name) {
        UserDetails userDetails = userService.loadUserByUsername(name);

        if (userDetails instanceof DeltaFiUserDetails dud) {
            return dud;
        }

        Assertions.fail("invalid user type");
        return null;
    }

    private void setupRequestHolder() {
        setupRequestHolder(null);
    }

    private void setupRequestHolder(String permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest();

        if (permissions != null) {
            request.addHeader(DeltaFiConstants.PERMISSIONS_HEADER, permissions);
        }

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void setupSecurityContext(String permission) {
        Authentication mocked = Mockito.mock(Authentication.class);
        Collection<? extends GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(permission));
        Mockito.doReturn(authorities).when(mocked).getAuthorities();
        SecurityContextHolder.getContext().setAuthentication(mocked);
    }

    private Map<String, List<String>> validateErrors(DeltaFiUser user) {
        try {
            userService.validateAndNormalizeDn(user);
        } catch (InvalidEntityException e) {
            return e.getErrorHolder().validationErrors();
        }
        return Map.of();
    }
}