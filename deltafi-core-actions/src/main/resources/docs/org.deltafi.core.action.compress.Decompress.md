# Decompress
Decompresses content from .ar, .gz, .7z, .tar, .tar.gz, .tar.xz, .tar.Z, .xz, .Z, or .zip.

## Parameters
| Name                   | Description                                                                                                                                                                                                            | Allowed Values                                                                | Required | Default |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|:--------:|:-------:|
| format                 | Format to decompress, overriding autodetection                                                                                                                                                                         | 7z<br/>ar<br/>gz<br/>tar<br/>tar.Z<br/>tar.gz<br/>tar.xz<br/>xz<br/>z<br/>zip |          |         |
| lineageFilename        | If set, will save a JSON lineage of each file and its parent                                                                                                                                                           | string                                                                        |          |         |
| maxExtractedBytes      | Limit the combined total of bytes that can be extracted across all files to protect against filling content storage. Metadata override key is 'disableMaxExtractedBytesCheck'. Defaults to 8GB if not positive         | string                                                                        |          | 8GB     |
| maxRecursionLevels     | Enables recursive decompression\/un-archiving of embedded content by checking filename extensions                                                                                                                      | integer                                                                       |          | 0       |
| passThroughUnsupported | When auto detecting a single content, if a supported archive\/compression format is not detected, then pass the content through instead of generating an error, and set 'decompressPassthrough' metadata key to 'true' | boolean                                                                       |          | false   |
| retainExistingContent  | Retain the existing content                                                                                                                                                                                            | boolean                                                                       |          | false   |

## Output
### Content
Content extracted from each input content will be added sequentially.

If an input content is an archive file, it will add multiple content, each with its name
from the archive.

If an input content is a non-archive compressed file (.gz, .xz, or .Z), it will add a
single content with the same name minus the compressed file suffix.

If retainExistingContent is true, all input content is written first, in the original
order.

When recursion is enabled (maxRecursionLevels > 0), and nested files are discovered with
the same name (path + filename), the content will be renamed to indicate its parent
archive and an optional random ID. To obtain the original filename, the
`LineageMap::findEntry()` must be used, and access the `fullName` attribute.

When recursion is enabled (maxRecursionLevels > 0), retainExistingContent must be false.

When saving lineage (non-empty lineageFilename), the format is written as a JSON string
using the data type org.deltafi.common.types.LineageMap, which offers easy methods to
parse and search by follow-on actions.

### Metadata
| Key            | Description                                                                  |
|----------------|------------------------------------------------------------------------------|
| compressFormat | Format of the last input content decompressed. Not set when using recursion. |

## Errors
* On content that cannot be decompressed
    * Occurs when a format is provided and all content is not in the format.
    * Occurs when content is detected as being a compressed archive but the format of the archive is
not tar. Only tar archives are permitted within compressed content (.tar.gz, .tar.xz, .tar.Z).

## Notes
* Compressed content in tar format will use an in-place decompression. This will result in much
quicker decompression than other formats since no additional writes will be made to the content
storage subsystem.
* This action is typically used before a Split action to extract content from an ingested file
before processing each one individually.

