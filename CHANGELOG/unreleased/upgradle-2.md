# Changes on branch `upgradle-2`
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
- Bumped DGS codegen 6.3.0 → 8.5.0
- Bumped DGS framework 10.5.0 → 10.6.0

### Upgrade and Migration
- **Plugin authors:** The `org.deltafi.actionkit.lookup.LookupTableClient` (and `CachingLookupTableClient`) constructor signature changed from `(String, GraphQLClient, HttpService)` to `(String, DgsGraphQLClient, HttpService)`. Plugins that **inject** `LookupTableClient` via Spring (the common pattern, used by all in-tree plugin examples) are unaffected. Plugins that **construct** these directly will need to update the second argument's type. Same migration applies if you inject the raw GraphQL client bean: `@Autowired GraphQLClient` → `@Autowired DgsGraphQLClient`. Both interfaces expose the same `executeQuery` overloads.
