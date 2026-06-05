# Changes on branch `upgradle-3`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- 

### Removed
- Dropped the unused `io.rest-assured:rest-assured` testImplementation dependency from `deltafi-core-actions/build.gradle`. No source code in the repo imported it; all 545 deltafi-core-actions tests pass without it.

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Centralized several previously-inline dependency pins into `gradle.properties`

### Upgrade and Migration
- `software.amazon.awssdk:bom`: 2.42.11 → 2.46.4
- `com.fasterxml.jackson.core:jackson-databind` (and Jackson family): 2.21.2 → 2.22.0
- `org.projectlombok:lombok`: 1.18.44 → 1.18.46
- `com.squareup.okhttp3:okhttp`: 4.12.0 → 5.3.2 (major)
- `io.github.openfeign:feign-core` (and Feign family): 13.11 → 13.12
- `org.junit:junit-bom`: 6.0.0 → 6.1.0
- `org.testcontainers:testcontainers` (and Testcontainers family): 1.21.3 → 1.21.4
- `de.undercouch.download` Gradle plugin: 5.6.0 → 5.7.0
- `com.gradle.plugin-publish` Gradle plugin: 1.2.1 → 2.1.1 (major)
- `org.apache.nifi:nifi-flowfile-packager`: 2.4.0 / 2.5.0 → 2.9.0
- `com.github.ben-manes.caffeine:caffeine`: 3.2.0 / 3.2.2 → 3.2.4
- `com.fasterxml.uuid:java-uuid-generator`: 5.1.0 / 5.1.1 → 5.2.0
- `com.github.docker-java:docker-java-core` and `docker-java-transport-httpclient5`: 3.6.0 → 3.7.1
- `ch.qos.logback:logback-classic`: 1.5.18 → 1.5.34
- `org.apache.activemq:activemq-client` and `activemq-broker`: 6.2.4 → 6.2.6
- `com.github.spotbugs:spotbugs-annotations`: 4.9.4 → 4.9.8
- `io.dropwizard.metrics:metrics-core`: 4.2.37 → 4.2.39
- `com.github.mwiede:jsch`: 2.27.3 → 2.28.2
- `io.nats:jnats`: 2.21.5 → 2.25.3
- `io.valkey:valkey-java`: 5.4.0 → 5.5.0
- `com.bucket4j:bucket4j_jdk17-jedis`: 8.14.0 → 8.19.0
- `org.tukaani:xz`: 1.10 → 1.12
- `org.apache.commons:commons-text`: 1.14.0 → 1.15.0
- `commons-io:commons-io`: 2.20.0 → 2.22.0
- `org.apache.commons:commons-lang3`: 3.19.0 → 3.20.0
- `io.fabric8:kubernetes-client`: 6.14.0 → 7.7.0 (major)
- `jakarta.annotation:jakarta.annotation-api`: removed dead `1.2.2.1-jre17` pin in `deltafi-core/build.gradle`; now resolves to whatever the Spring Boot BOM provides (currently 2.1.1). The previous pin did not exist on Maven Central and was already being overridden by the BOM at resolution time, so this is a documentation fix rather than a behavior change.
- `com.jayway.jsonpath:json-path`: 2.9.0 → 3.0.0 (major)
- `net.logstash.logback:logstash-logback-encoder`: 8.0 → 9.0 (major)
- `org.flywaydb:flyway-core` and `flyway-database-postgresql`: 11.13.2 → 12.8.1 (major)
