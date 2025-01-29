# Changes on branch `remove-publish-from-plugin-convention`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- 

### Removed
- Removed the `maven-publish` and publishing section from the org.deltafi.plugin-convention to simplify getting started with new plugins

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- Plugin projects that used the `org.deltafi.plugin-convention` plugin and require publishing need to add the `maven-publish` to the plugins section of the build.gradle and add the following publishing section:

```
publishing {
    publications {
        mavenJarPublication(MavenPublication) {
            artifact bootJar
        }
    }
    repositories {
        maven {
            url projectMavenRepo
            credentials(HttpHeaderCredentials) {
                name = gitLabTokenType
                value = gitLabToken
            }
            authentication {
                header(HttpHeaderAuthentication)
            }
        }
    }
}
```
