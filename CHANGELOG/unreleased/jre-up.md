# Changes on branch `dev`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- Pin transitive `com.fasterxml.jackson.core:jackson-databind` to `2.21.5` (CVE-2026-54515)
- Pin transitive `org.bouncycastle:bcprov-lts8on` to `2.73.12` (CVE-2026-8149)
- Bump `org.bouncycastle:bcprov-jdk18on`/`bcpkix-jdk18on` pins to `1.85`

### Tech-Debt/Refactor
- Scope `graphql-dgs-codegen-shared-core` to `compileOnly` in `deltafi-common-spring` so its transitive Jackson 2 dependency no longer leaks onto consumers' runtime classpaths
- Drop the `io.spring.dependency-management` plugin from the ingress modules in favor of the platform BOM, matching `deltafi-core`
- Remove the stale `tomcat-embed-core` version pin that was forcing a downgrade to `10.1.55`; Spring Boot's managed `11.0.22` now resolves consistently with `tomcat-embed-websocket`
- Remove the obsolete `plexus-utils` version pin from `deltafi-core` (the resolved `3.6.1` is not vulnerable and already shipped unpinned in the ingress modules)
- Consolidate `bcpkix-jdk18on` version management into `java-conventions` alongside `bcprov-jdk18on` so the BouncyCastle pair stays locked to `bouncyCastleVersion`
- Remove unused/redundant dependency declarations: `commons-collections4` (`deltafi-core`), `javax.annotation-api` (`nats-ingress`, superseded by `jakarta.annotation`), and the explicit `tools.jackson.core:jackson-core`/`jackson-databind` decls in the ingress modules (already provided transitively)

### Upgrade and Migration
- Update TUI module dependencies
- Update compose timescale and postgres images
- Update `deltafi/deltafi-java-jre` base image to `21.0.11-alpine-4`
- Update `deltafi/nginx` image to `1.31.3-alpine3.24-0`
- Update `deltafi/victoria-metrics` image to `v1.147.0-0`
- Update `deltafi/grafana` image to `12.4.5-1`
- Update `deltafi/dozzle` image to `v10.6.10-0`
- Update `deltafi/s3proxy` image to `3.3.0-0`
