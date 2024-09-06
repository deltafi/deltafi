# Decompress
Decompresses content from ar, gz, tar, tar.gz, tar.xz, tar.Z, xz, Z, or zip.

## Parameters
| Name                  | Description                                    | Allowed Values                                                         |  Required  | Default                                      |
|-----------------------|------------------------------------------------|------------------------------------------------------------------------|:----------:|----------------------------------------------|
| format                | Format to decompress, overriding autodetection | ar<br/>gz<br/>tar<br/>tar.gz<br/>tar.xz<br/>tar.Z<br/>xz<br/>Z<br/>zip |            | none - will autodetect each content's format |
| retainExistingContent | Retain the existing content                    | true<br/>false                                                         |            | false                                        |

## Inputs
### Content
One or more in a supported format

## Outputs
### Content
Content extracted from each input content will be added sequentially.

If an input content is an archive file, it will add multiple content, each with its name from the archive.

If an input content is a non-archive compressed file (.gz, .xz, or .Z), it will add a single content with the same name
minus the compressed file suffix.

If retainExistingContent is true, all input content is written first, in the original order.

### Metadata
| Name           | Description                                   |
|----------------|-----------------------------------------------|
| compressFormat | Format of the last input content decompressed |

## Errors
- On content that cannot be decompressed
  - Occurs when a format is provided and all content is not in the format.
  - Occurs when content is detected as being a compressed archive but the format of the archive is not tar. Only tar
  archives are permitted within compressed content (.tar.gz, .tar.xz, .tar.Z).

## Notes
Compressed content in tar format will use an in-place decompression. This will result in much quicker decompression
than other formats since no additional writes will be made to the content storage subsystem.

## Usage
This action is typically used before a Split action to extract content from an ingested file before processing each
one individually.