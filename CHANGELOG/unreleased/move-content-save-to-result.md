# Changes on branch `move-content-save-to-result`
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
- Move content storage methods from the Action classes to the Result classes, combining store and append result into one step.
For example, instead of:

```java
ContentReference reference = saveContent(did, decompressed, MediaType.APPLICATION_OCTET_STREAM);
Content content = new Content(contentName, reference);
result.addContent(content);
```

Now:

```java
result.saveContent(decompressed, contentName, MediaType.APPLICATION_OCTET_STREAM)
```


### Upgrade and Migration
- 
