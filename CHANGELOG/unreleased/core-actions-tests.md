# Changes on branch `core-actions-tests`
Document any changes on this branch here.
### Added
- 

### Changed
- Renamed or replaced most core action classes:
  - org.deltafi.core.action.ingress.SftpTimedIngressAction -> org.deltafi.core.action.ingress.SftpIngress
  - org.deltafi.core.action.merge.MergeContentFormatAction ->
    - org.deltafi.core.action.archive.Archive (.ar, .tar, .tar.gz, .tar.xz, or .zip)
    - org.deltafi.core.action.merge.Merge (binary concatenation)
  - org.deltafi.core.action.CompressionFormatAction ->
    - org.deltafi.core.action.archive.Archive (.ar, .tar, .tar.gz, .tar.xz, or .zip)
    - org.deltafi.core.action.compress.Compress (.gz or .xz)
  - org.deltafi.core.action.ConvertContentTransformAction -> org.deltafi.core.action.convert.Convert
  - org.deltafi.core.action.DecompressionTransformAction ->
    - org.deltafi.core.action.archive.Unarchive (.ar, .tar, .tar.gz, .tar.xz, .tar.Z, or .zip)
    - org.deltafi.core.action.compress.Decompress (.gz, .xz, or .Z)
  - org.deltafi.core.action.DeleteMetadataTransformAction -> org.deltafi.core.action.metadata.ModifyMetadata
  - org.deltafi.core.action.DeltaFiEgressAction -> org.deltafi.core.action.egress.DeltaFiEgress
  - org.deltafi.core.action.DetectMediaTypeTransformAction -> org.deltafi.core.action.mediatype.DetectMediaType
  - org.deltafi.core.action.ErrorByFiatTransformAction -> org.deltafi.core.action.error.Error
  - org.deltafi.core.action.ExtractJsonMetadataTransformAction -> org.deltafi.core.action.extract.ExtractJson
  - org.deltafi.core.action.ExtractXmlAnnotationsDomainAction -> org.deltafi.core.action.extract.ExtractXml
  - org.deltafi.core.action.ExtractXmlMetadataTransformAction -> org.deltafi.core.action.extract.ExtractXml
  - org.deltafi.core.action.FilterByCriteriaTransformAction -> org.deltafi.core.action.filter.FilterByCriteria
  - org.deltafi.core.action.FilterByFiatTransformAction -> org.deltafi.core.action.filter.Filter
  - org.deltafi.core.action.FilterEgressAction -> org.deltafi.core.action.egress.FilterEgress
  - org.deltafi.core.action.FlowfileEgressAction -> org.deltafi.core.action.egress.FlowfileEgress
  - org.deltafi.core.action.JoltTransformAction -> org.deltafi.core.action.jolt.JoltTransform
  - org.deltafi.core.action.LineSplitterTransformAction -> removed
  - org.deltafi.core.action.MetadataToAnnotationTransformAction -> org.deltafi.core.action.metadata.MetadataToAnnotation
  - org.deltafi.core.action.MetadataToContentTransformAction -> org.deltafi.core.action.metadata.MetadataToContent
  - org.deltafi.core.action.ModifyMediaTypeTransformAction -> org.deltafi.core.action.mediatype.ModifyMediaType
  - org.deltafi.core.action.ModifyMetadataTransformAction -> org.deltafi.core.action.metadata.ModifyMetadata
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
- 

### Upgrade and Migration
- 
