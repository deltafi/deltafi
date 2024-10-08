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
package org.deltafi.core.security;

import jakarta.servlet.Filter;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.configuration.AuthProperties;
import org.deltafi.core.services.DeltaFiUserService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(8);
    }

    @Bean
    public UserDetailsService userDetailsService(DeltaFiUserService userService) {
        return new DeltaFiUserDetailsService(userService);
    }

    /*
     * Create a SecurityFilterChain for the auth endpoint that handles authentication
     * based on the AuthMode setting. All other requests will use the regularFilterChain
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authFilterChain(HttpSecurity httpSecurity, UserDetailsService userDetailsService, AuthProperties authProperties) throws Exception {
        httpSecurity
                .securityMatcher("/api/v2/auth")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);

        if (authProperties.noAuth()) {
            log.info("Starting up with no authentication");
            httpSecurity.addFilter(new NoAuthFilter(createAuthManager(userDetailsService)));
        } else if (authProperties.basicMode()) {
            log.info("Starting up with basic authentication");
            httpSecurity.httpBasic(httpBasic -> httpBasic.realmName("Restricted"));
        } else if (authProperties.certMode()) {
            log.info("Starting up with certificate authentication");
            httpSecurity.addFilter(new DnHeaderFilter(createAuthManager(userDetailsService)));
        }

        return httpSecurity.build();
    }

    /*
     * Create a SecurityFilterChain for all requests other than auth. The user info
     * will be based on the information in the headers
     */
    @Bean
    public SecurityFilterChain regularFilterChain(HttpSecurity httpSecurity, UserDetailsService userDetailsService) throws Exception {
        // permitAll for the error redirect so users are prompted for credentials in basic auth mode
        // permitAll to plugins so plugins can register
        return httpSecurity
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/plugins").permitAll()
                        .anyRequest().authenticated())
                .addFilter(requestFilter(userDetailsService))
                .build();
    }

    private AuthenticationManager createAuthManager(UserDetailsService userDetailsService) {
        UserDetailsByNameServiceWrapper<PreAuthenticatedAuthenticationToken> userDetailsByNameServiceWrapper = new UserDetailsByNameServiceWrapper<>(userDetailsService);
        PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
        provider.setPreAuthenticatedUserDetailsService(userDetailsByNameServiceWrapper);
        return new ProviderManager(provider);
    }

    /**
     * Create the RequestHeaderAuthenticationFilter that pulls the principal out of the headers
     * @param userDetailsService userDetailService that will load the user info
     * @return filter that handles authentication
     */
    private Filter requestFilter(UserDetailsService userDetailsService) {
        RequestHeaderAuthenticationFilter headerFilter = new RequestHeaderAuthenticationFilter();
        headerFilter.setPrincipalRequestHeader(DeltaFiConstants.USER_NAME_HEADER);
        headerFilter.setAuthenticationManager(createAuthManager(userDetailsService));
        headerFilter.setExceptionIfHeaderMissing(false);

        return headerFilter;
    }
}
