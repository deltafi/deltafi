# Changes on branch `spring-boot-4`
Document any changes on this branch here.
### Added
- 

### Changed
- Cache shape now declared in code via `@Bean CacheSpec` beans next to each `@Cacheable` usage. Existing `cache.caches.*` YAML still overrides any same-named coded default.
- Caffeine is now part of the action-kit runtime classpath (promoted `testImplementation` → `api`). Plugin developers no longer need to add it explicitly to use the standard caching layer; those preferring a different cache implementation can still substitute their own `CacheManager` bean.

### Fixed
- 

### Removed
- Unused `spring-retry` dependency from `deltafi-core` (no `@Retryable` methods existed in the codebase).
- `com.github.hierynomus.license` plugin dropped from the generated Java plugin template (`build.gradle.tmpl`); plugin developers no longer inherit a stale Spring 6 transitive dependency at build time.

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- Spring boot 4 stack upgrade
  - Gradle 9.5.1 required (bumped alongside this upgrade).
  - spring-boot: 3.5.14 → 4.1.0
  - spring-framework (transitive): 6.x → 7.x
  - hibernate-core (transitive): 6.6.x → 7.2.12
  - hypersistence-utils-hibernate-63 → hypersistence-utils-hibernate-71 (artifact suffix flip for Hibernate 7 API)
  - DGS framework: 10.6.0 → 12.0.1
  - artemis-jms-server → artemis-jakarta-server (test runtime in `artemis-ingress`)
  - spring-boot-starter-cache + caffeine: promoted `testImplementation` → `api` in `deltafi-action-kit`
  - spring-boot-flyway: added as `testImplementation` in `deltafi-core` (split out of `spring-boot-autoconfigure` in SB4)
  - spring-boot-jdbc-test: added as `testImplementation` in `deltafi-core` (provides `@JdbcTest`, `@AutoConfigureTestDatabase`)
  - spring-boot-resttestclient: added as `testImplementation` in `deltafi-core` (provides `TestRestTemplate`)
  - spring-boot-restclient: added as `testImplementation` in `deltafi-core` (provides `RestTemplateBuilder`)
  - spring-boot-webmvc-test: added as `testImplementation` in `deltafi-core` (provides `@WebMvcTest`, `@AutoConfigureMockMvc`)
