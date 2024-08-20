# Compress
Compresses content using ar, gz, tar, tar.gz, tar.xz, xz, or zip.

## Parameters
| Name      | Description                                                                            | Allowed Values                                           |  Required  | Default                                          |
|-----------|----------------------------------------------------------------------------------------|----------------------------------------------------------|:----------:|--------------------------------------------------|
| format    | Format to compress to                                                                  | ar<br/>gz<br/>tar<br/>tar.gz<br/>tar.xz<br/>xz<br/>zip   |    Yes     |                                                  |
| name      | Name of the compressed content                                                         | String                                                   |            | compressed                                       |
| mediaType | Media type of the compressed content, overriding the default for the configured format | String                                                   |            | Media type associated with the configured format |

## Inputs
### Content
Any

## Outputs
### Content
If the format is ar, tar, tar.gz, tar.xz, or zip, all content is compressed to a single content. The name of the content
will be set from the name parameter and include the appropriate suffix.

If the format is gz or xz, all content is compressed individually. Each content will keep its name but will include the
appropriate suffix.

### Metadata
| Name           | Description                 |
|----------------|-----------------------------|
| compressFormat | The format used to compress |

## Errors
- On no input content
