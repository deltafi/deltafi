# Changes on branch `expand-core-action-docs`
Document any changes on this branch here.
### Added
- Added Markdown documentation for all core transform actions.
- Added Annotate core action (org.deltafi.core.action.annotate.Annotate)

### Changed
- Core actions changed:
  - org.deltafi.core.action.ingress.SftpTimedIngressAction -> org.deltafi.core.action.ingress.SftpIngress
  - org.deltafi.core.action.merge.MergeContentFormatAction ->
    - org.deltafi.core.action.compress.Compress (.ar, .tar, .tar.gz, .tar.xz, or .zip)
    - org.deltafi.core.action.merge.Merge (binary concatenation)
  - org.deltafi.core.action.CompressionFormatAction -> org.deltafi.core.action.compress.Compress
  - org.deltafi.core.action.ConvertContentTransformAction -> org.deltafi.core.action.convert.Convert
  - org.deltafi.core.action.DecompressionTransformAction -> org.deltafi.core.action.compress.Decompress
    - Added `retainExistingContent` parameter
  - org.deltafi.core.action.DeleteMetadataTransformAction -> org.deltafi.core.action.metadata.ModifyMetadata
  - org.deltafi.core.action.DeltaFiEgressAction -> org.deltafi.core.action.egress.DeltaFiEgress
  - org.deltafi.core.action.DetectMediaTypeTransformAction -> org.deltafi.core.action.mediatype.ModifyMediaType
    - With `autodetect` parameter set to `true` (default)
  - org.deltafi.core.action.ErrorByFiatTransformAction -> org.deltafi.core.action.error.Error
  - org.deltafi.core.action.ExtractJsonMetadataTransformAction -> org.deltafi.core.action.extract.ExtractJson
    - With `extractTarget` parameter set to `METADATA` (default)
  - org.deltafi.core.action.ExtractXmlAnnotationsDomainAction -> org.deltafi.core.action.extract.ExtractXml
    - With `extractTarget` parameter set to `ANNOTATIONS`
  - org.deltafi.core.action.ExtractXmlMetadataTransformAction -> org.deltafi.core.action.extract.ExtractXml
    - With `extractTarget` parameter set to `METADATA` (default)
  - org.deltafi.core.action.FilterByCriteriaTransformAction -> org.deltafi.core.action.filter.Filter
  - org.deltafi.core.action.FilterByFiatTransformAction -> org.deltafi.core.action.filter.Filter
  - org.deltafi.core.action.FilterEgressAction -> org.deltafi.core.action.egress.FilterEgress
  - org.deltafi.core.action.FlowfileEgressAction -> org.deltafi.core.action.egress.FlowfileEgress
  - org.deltafi.core.action.JoltTransformAction -> org.deltafi.core.action.jolt.JoltTransform
  - org.deltafi.core.action.LineSplitterTransformAction -> removed
  - org.deltafi.core.action.MetadataToAnnotationTransformAction -> org.deltafi.core.action.annotate.Annotate
  - org.deltafi.core.action.MetadataToContentTransformAction -> org.deltafi.core.action.metadata.MetadataToContent
    - Replaced `replaceExistingContent` with `retainExistingContent`. Value must be inverted!
  - org.deltafi.core.action.ModifyMediaTypeTransformAction -> org.deltafi.core.action.mediatype.ModifyMediaType
  - org.deltafi.core.action.ModifyMetadataTransformAction -> org.deltafi.core.action.metadata.ModifyMetadata
  - org.deltafi.core.action.RecursiveDecompress -> org.deltafi.core.action.compress.RecursiveDecompress
  - org.deltafi.core.action.RestPostEgressAction -> org.deltafi.core.action.egress.RestPostEgress
  - org.deltafi.core.action.RouteByCriteriaTransformAction -> removed
  - org.deltafi.core.action.SmokeTestIngressAction -> org.deltafi.core.action.ingress.SmokeTestIngress
  - org.deltafi.core.action.SplitterLoadAction -> org.deltafi.core.action.split.Split
  - org.deltafi.core.action.XsltTransformAction -> org.deltafi.core.action.xslt.XsltTransform

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Replaced deprecated CountingInputStream with BoundedInputStream in all usages

### Upgrade and Migration
- 
