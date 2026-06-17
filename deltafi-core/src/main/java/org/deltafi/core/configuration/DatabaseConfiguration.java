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
package org.deltafi.core.configuration;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@Configuration
@EnableJpaRepositories(basePackages = {"org.deltafi.core.repo", "org.deltafi.core.security",
        "org.deltafi.core.integration", "org.deltafi.core.monitor"})
public class DatabaseConfiguration {
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(new NamedParameterJdbcTemplate(dataSource));
    }

    // Migrations V1-V66 were modified to make TimescaleDB optional.
    // Upgrades from versions prior to V70 require flyway repair to update checksums.
    private static final int TIMESCALEDB_OPTIONAL_MIGRATION_VERSION = 70;

    @Bean
    @Primary
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .failOnMissingLocations(true)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateMigrationNaming(true)
                .load();
        if (needsRepairForTimescaleDbOptional(dataSource)) {
            flyway.repair();
        }
        flyway.migrate();
        return flyway;
    }

    private boolean needsRepairForTimescaleDbOptional(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            // Check if flyway_schema_history table exists
            ResultSet tables = conn.getMetaData().getTables(null, null, "flyway_schema_history", null);
            if (!tables.next()) {
                return false; // New install, no repair needed
            }

            // Check if there are migrations before V69 but V69 hasn't been applied yet
            try (var stmt = conn.prepareStatement(
                    "SELECT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version ~ '^[0-9]+$' AND version::int < ?) " +
                    "AND NOT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version = ?)")) {
                stmt.setInt(1, TIMESCALEDB_OPTIONAL_MIGRATION_VERSION);
                stmt.setString(2, String.valueOf(TIMESCALEDB_OPTIONAL_MIGRATION_VERSION));
                ResultSet rs = stmt.executeQuery();
                return rs.next() && rs.getBoolean(1);
            }
        } catch (SQLException e) {
            // If we can't determine, don't repair - let flyway handle any issues
            return false;
        }
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, Flyway unused) {
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(dataSource);
        bean.setPackagesToScan("org.deltafi.core");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        bean.setJpaPropertyMap(Map.of(
                "hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName(),
                "hibernate.physical_naming_strategy", PhysicalNamingStrategySnakeCaseImpl.class.getName()));
        bean.setPersistenceUnitName("primary");
        return bean;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactoryBean) {
        return new JpaTransactionManager(entityManagerFactoryBean.getObject());
    }
}
