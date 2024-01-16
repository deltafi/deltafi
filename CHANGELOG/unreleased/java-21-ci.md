# Changes on branch `java-21-ci`
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
- 

### Tech-Debt/Refactor
- ByteBuddy dependency is running in "experimental" mode for Java 21 compatability

### Upgrade and Migration
- Migrate core projects to use Java 21
    NOTE: Plugins running against the new Java action kit must be recompiled with Java 21 toolchain
- Update CI build image to JDK 21.0.1
- Update base image for Java applications to deltafi/deltafi-java-jre:21.0.1-alpine-0
