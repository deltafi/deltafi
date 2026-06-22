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
package org.deltafi.core;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.RoleService;
import org.deltafi.core.services.SslConfigService;
import org.deltafi.core.services.SystemService;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.SpringImplicitNamingStrategy;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates the committed OpenAPI 3 specification for the DeltaFi REST API by booting the application
 * context and capturing springdoc's rendered document from {@code /v3/api-docs.yaml}.
 *
 * <p>The generated spec is the source of truth for the Go (TUI) and TypeScript (UI) REST clients,
 * mirroring how the GraphQL {@code .graphqls} files drive genqlient/DGS codegen.
 *
 * <p>This is tagged {@code openapi} and excluded from the normal {@code test} task so routine test
 * runs do not rewrite the tracked spec. CI regenerates it via the {@code generateOpenApiSpec} task
 * and fails if the working tree differs (drift check).
 *
 * <p><strong>No database is required.</strong> springdoc renders the spec purely from the wired
 * controllers - no request ever executes a query - so {@link NoDatabaseConfig} swaps the production
 * persistence beans for no-DB doubles instead of starting a Postgres container (see that class). The
 * remaining {@code @MockitoBean}s stand in for non-database external infra (Valkey, object storage,
 * the deployer, etc.) that likewise cannot start in a unit test.
 */
@Tag("openapi")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.main.allow-bean-definition-overriding=true",
        // No infra connects during boot: Valkey is mocked (a valid URL is all JedisPool needs to construct),
        // and the maintenance/event schedules are off so services skip their startup writes (seeding default
        // admin/roles) - the only startup paths that would persist. The lookup datasource stays disabled.
        "valkey.url=redis://localhost:6379",
        "schedule.actionEvents=false",
        "schedule.maintenance=false",
        "schedule.flowSync=false",
        "schedule.diskSpace=false",
        "schedule.errorCount=false",
        "schedule.propertySync=false",
        "cold.queue.refresh.duration=PT1M",
        "lookup.enabled=false"
})
@AutoConfigureTestRestTemplate
@Import(DeltaFiCoreApplicationTests.Config.class)
class OpenApiSpecGeneratorTest {

    static final Path SPEC_PATH = Path.of("src/main/resources/openapi/openapi.yaml");

    /**
     * Overrides {@code DatabaseConfiguration}'s persistence beans so the context boots without a database.
     * The production beans otherwise (a) run Flyway {@code migrate()} eagerly and (b) connect Hibernate at
     * startup - both need a live DB. Here:
     * <ul>
     *   <li>{@code dataSource} is a deep-stub mock: {@code getConnection()...executeQuery().next()} is
     *       {@code false} (every read returns no rows) and {@code executeUpdate()} is {@code 0} (writes
     *       are no-ops). Nothing ever opens a real connection.</li>
     *   <li>{@code flyway} is a no-op mock (the real bean migrates against a live DB).</li>
     *   <li>{@code entityManagerFactory} maps the entities against an explicit dialect with JDBC metadata
     *       access disabled, so Hibernate builds the {@code SessionFactory} without probing a connection.</li>
     * </ul>
     * The few services that query in {@code @PostConstruct} (flow/resume cache refreshes) simply see empty
     * results; the schedule-gated startup writes (default admin/roles) are disabled via {@code schedule.*}.
     */
    @TestConfiguration
    static class NoDatabaseConfig {
        @Bean
        @Primary
        DataSource dataSource() {
            return Mockito.mock(DataSource.class, Mockito.RETURNS_DEEP_STUBS);
        }

        @Bean
        @Primary
        Flyway flyway() {
            return Mockito.mock(Flyway.class);
        }

        @Bean
        @Primary
        DeltaFiPropertiesService deltaFiPropertiesService() {
            return new MockDeltaFiPropertiesService();
        }

        @Bean
        @Primary
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, Flyway unused) {
            LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
            bean.setDataSource(dataSource);
            bean.setPackagesToScan("org.deltafi.core");
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            bean.setJpaPropertyMap(Map.of(
                    "hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName(),
                    "hibernate.physical_naming_strategy", PhysicalNamingStrategySnakeCaseImpl.class.getName(),
                    // Map entities straight from the dialect; never probe the (mock) connection at boot.
                    "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                    "hibernate.boot.allow_jdbc_metadata_access", "false",
                    "hibernate.hbm2ddl.auto", "none"));
            bean.setPersistenceUnitName("primary");
            return bean;
        }
    }

    // External-infra beans that cannot start in the test context; mirrors DeltaFiCoreApplicationTests.
    @MockitoBean DeployerService deployerService;
    @MockitoBean SslConfigService sslConfigService;
    @MockitoBean RoleService roleService;
    @MockitoBean SystemService systemService;

    // mocked so we can authorize the request to the get spec to write
    @MockitoBean UserDetailsService userDetailsService;

    @Autowired
    TestRestTemplate restTemplate;

    // springdoc emits schema properties in JVM-reflection order, which is not stable across runs.
    // Re-serialize with every map sorted by key so the committed spec is byte-deterministic.
    private static final YAMLMapper CANONICAL_MAPPER = YAMLMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build();

    @Test
    void generateOpenApiSpec() throws Exception {
        // /v3/api-docs requires an authenticated user (any permission set); supply one via the
        // X-User-Name header, matching how nginx forwards the principal in a real deployment.
        Mockito.when(userDetailsService.loadUserByUsername("openapi-generator")).thenReturn(
                new User("openapi-generator", "", List.of(new SimpleGrantedAuthority(DeltaFiConstants.ADMIN_PERMISSION))));
        HttpHeaders headers = new HttpHeaders();
        headers.add(DeltaFiConstants.USER_NAME_HEADER, "openapi-generator");
        ResponseEntity<String> response = restTemplate.exchange(
                "/v3/api-docs.yaml", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();

        Object tree = CANONICAL_MAPPER.readValue(response.getBody(), Object.class);
        String canonical = CANONICAL_MAPPER.writeValueAsString(tree);

        Files.createDirectories(SPEC_PATH.getParent());
        Files.writeString(SPEC_PATH, canonical);
    }

    @Test
    void swaggerUiRequiresAdmin() {
        // Anonymous request is rejected — the Swagger UI is not open.
        ResponseEntity<String> anon = restTemplate.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(anon.getStatusCode().is4xxClientError()).isTrue();

        // An admin (resolved via the X-User-Name header) gets the wired Swagger UI.
        Mockito.when(userDetailsService.loadUserByUsername("admin")).thenReturn(
                new User("admin", "", List.of(new SimpleGrantedAuthority(DeltaFiConstants.ADMIN_PERMISSION))));
        HttpHeaders headers = new HttpHeaders();
        headers.add(DeltaFiConstants.USER_NAME_HEADER, "admin");
        ResponseEntity<String> admin = restTemplate.exchange(
                "/swagger-ui/index.html", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
