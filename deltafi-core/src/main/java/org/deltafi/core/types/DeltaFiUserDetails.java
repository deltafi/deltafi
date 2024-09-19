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
package org.deltafi.core.types;

import lombok.Builder;
import lombok.Getter;
import org.deltafi.common.constant.DeltaFiConstants;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Builder
@Getter
public class DeltaFiUserDetails implements UserDetails {
    private final String username;
    private final String password;
    private final Set<String> permissionSet;
    private final Set<GrantedAuthority> authorities;
    private final String id;
    private final String metricsRole;
    private final String permissions;
    private final boolean admin;

    public String id() {
        return id;
    }

    public String metricsRole() {
        return metricsRole;
    }

    public boolean hasPermission(String permission) {
        return isAdmin() || permissionSet.contains(permission);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @SuppressWarnings("unused")
    public static class DeltaFiUserDetailsBuilder {

        public DeltaFiUserDetailsBuilder id(UUID id) {
            this.id = id != null ? id.toString() : "none";
            return this;
        }

        public DeltaFiUserDetailsBuilder permissionSet(Set<String> permissionSet) {
            this.permissionSet = permissionSet != null ? permissionSet : Set.of();
            this.authorities = this.permissionSet.stream()
                    .map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
            this.permissions = String.join(",", this.permissionSet);
            this.admin = this.permissionSet.stream().anyMatch(DeltaFiConstants.ADMIN_PERMISSION::equals);
            updateMetricsRole();
            return this;
        }

        private void updateMetricsRole() {
            if (this.admin || permissionSet.contains("MetricsAdmin")) {
                this.metricsRole = "Admin";
            } else if (permissionSet.contains("MetricsEdit")) {
                this.metricsRole = "Editor";
            } else if (permissionSet.contains("MetricsView")) {
                this.metricsRole = "Viewer";
            } else {
                this.metricsRole = "";
            }
        }
    }
}
