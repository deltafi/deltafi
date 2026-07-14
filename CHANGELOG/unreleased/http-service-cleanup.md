# Changes on branch `http-service-cleanup`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Fixed a connection leak in the action-kit `LookupTableClient` where a successful lookup-table upload never read or closed the HTTP response body, so its connection was never released and repeated uploads could accumulate open sockets.

### Removed
- 

### Deprecated
- Deprecated the `HttpService.post`/`put`/`patch`/`delete` methods that return `HttpResponse<InputStream>` (marked for removal in a future major release). Their response body is a live, connection-backed stream the caller must drain and close where missing that leaks connections, and closing without draining silently forfeits connection reuse. Use the new leak-safe variants instead (see Upgrade and Migration).

### Security
- 

### Tech-Debt/Refactor
- Reworked `HttpService` response-body handling to be leak-safe by construction. Each verb now has three new variants: `*ForBytes` (buffered `byte[]` response), `*DiscardingBody` (drain and discard), and `*ForStream` (a closeable `HttpInputStreamResponse` for genuine streaming). The `*ForBytes` and `*DiscardingBody` variants consume the body and return the connection to the pool automatically, so there is nothing for the caller to close.

### Upgrade and Migration
- If your plugin uses `HttpService`, migrate off the deprecated `post`/`put`/`patch`/`delete` methods to the variant matching your need: `*ForBytes` when you want the response body, `*DiscardingBody` for fire-and-forget calls, or `*ForStream` (in a try-with-resources) when you stream a large body. The deprecated methods still work, but require you to drain the body to EOF and close it — otherwise the connection leaks.
