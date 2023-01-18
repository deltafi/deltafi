/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.security;

import org.deltafi.common.constant.DeltaFiConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

import jakarta.servlet.Filter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public UserDetailsService userDetailsService() {
        return new DeltaFiUserDetailsService();
    }

    @Bean
    public PreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider(UserDetailsService userDetailsService) {
        UserDetailsByNameServiceWrapper<PreAuthenticatedAuthenticationToken> userDetailsByNameServiceWrapper = new UserDetailsByNameServiceWrapper<>(userDetailsService);
        PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
        provider.setPreAuthenticatedUserDetailsService(userDetailsByNameServiceWrapper);
        return provider;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        // Do not secure internal use endpoints that are not exposed
        return webSecurity -> webSecurity
                .ignoring()
                .requestMatchers(new AntPathRequestMatcher("/config/**"), new AntPathRequestMatcher("/plugins"));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity, PreAuthenticatedAuthenticationProvider authProvider) throws Exception {
        return httpSecurity
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .csrf().disable()
                .addFilter(requestFilter(authProvider))
                .build();
    }

    /**
     * Create the RequestHeaderAuthenticationFilter that pulls the principal out of the headers
     * @param preAuthenticatedAuthenticationProvider authentication provider that takes the info from filter
     * @return filter that handles authentication
     */
    private Filter requestFilter(PreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider) {
        AuthenticationManager authenticationManager = new ProviderManager(preAuthenticatedAuthenticationProvider);

        RequestHeaderAuthenticationFilter headerFilter = new RequestHeaderAuthenticationFilter();
        headerFilter.setPrincipalRequestHeader(DeltaFiConstants.USER_HEADER);
        headerFilter.setAuthenticationManager(authenticationManager);
        headerFilter.setExceptionIfHeaderMissing(false);

        return headerFilter;
    }
}
