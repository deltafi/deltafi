# Changes on branch `javadoc-warning-cleanup`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Fixed silent default-value bugs in `DeltaFileFlowDTO` and `Property` where Lombok `@Builder` was ignoring field initializers. Builders now correctly default `DeltaFileFlowDTO.input`/`actions`/`publishTopics`/`pendingAnnotations`/`pendingActions` to empty values and `Property.editable` to `true`.

### Removed
- 

### Deprecated
- Marked the `@org.deltafi.actionkit.action.parameters.annotation.Size` annotation for removal in 3.0. Plugins should migrate to `@org.deltafi.common.json.Size` for `minLength`/`maxLength` JSON schema hints.
- Marked `ActionDescriptor.description` for removal in 3.0. Plugins should populate `ActionOptions.description` via `actionOptions` instead.

### Security
- 

### Tech-Debt/Refactor
- Cleaned up Java compile and javadoc warnings across the build. Standardized javadoc doclint (`Xdoclint:all,-missing`) and registered the standard `@apiNote`/`@implSpec`/`@implNote` tags. Enabled `-Xlint:deprecation` permanently so deprecated-API regressions are visible.
- Migrated deprecated Jackson APIs (`ObjectMapper.setSerializationInclusion` → builder-based `defaultPropertyInclusion`, `JsonNode.fields()` → `properties()`), DGS `Coercing` signatures (now take `GraphQLContext` and `Locale`), Feign `Client.Default`/`Retryer.Default` → `DefaultClient`/`DefaultRetryer`, Spring `PropertyPlaceholderHelper` 4-arg constructor → 5-arg, and Mockito `withSettings().lenient()` → `.strictness(Strictness.LENIENT)`.

### Upgrade and Migration
- 
