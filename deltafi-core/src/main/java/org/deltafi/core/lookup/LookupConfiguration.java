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
package org.deltafi.core.lookup;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@ConditionalOnProperty("lookup.enabled")
@Configuration
@EnableJpaRepositories(entityManagerFactoryRef = "lookupEntityManagerFactory", transactionManagerRef = "lookupTransactionManager")
public class LookupConfiguration {
    @Bean
    @Qualifier("lookup")
    @ConfigurationProperties("lookup.datasource")
    public DataSourceProperties lookupDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Qualifier("lookup")
    @ConfigurationProperties("lookup.datasource.hikari")
    public DataSource lookupDataSource(@Qualifier("lookup") DataSourceProperties lookupDataSourceProperties) {
        return lookupDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Qualifier("lookup")
    public JdbcClient lookupJdbcClient(@Qualifier("lookup") DataSource lookupDataSource) {
        return JdbcClient.create(new NamedParameterJdbcTemplate(lookupDataSource));
    }

    @Bean
    @Qualifier("lookup")
    public Flyway lookupFlyway(@Qualifier("lookup") DataSource lookupDataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(lookupDataSource)
                .locations("classpath:db/lookup/migration")
                .failOnMissingLocations(true)
                .failOnMissingLocations(true)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateMigrationNaming(true)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    @Qualifier("lookup")
    public LocalContainerEntityManagerFactoryBean lookupEntityManagerFactory(
            @Qualifier("lookup") DataSource lookupDataSource,
            @Qualifier("lookup") Flyway unused) {
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(lookupDataSource);
        bean.setPackagesToScan("org.deltafi.core.lookup");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        bean.setJpaPropertyMap(Map.of(
                "hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName(),
                "hibernate.physical_naming_strategy", CamelCaseToUnderscoresNamingStrategy.class.getName()));
        bean.setPersistenceUnitName("lookup");
        return bean;
    }

    @Bean
    @Qualifier("lookup")
    public PlatformTransactionManager lookupTransactionManager(
            @Qualifier("lookup") LocalContainerEntityManagerFactoryBean lookupEntityManagerFactoryBean) {
        return new JpaTransactionManager(lookupEntityManagerFactoryBean.getObject());
    }
}
