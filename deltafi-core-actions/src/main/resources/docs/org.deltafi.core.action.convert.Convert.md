# Convert
Converts content between CSV, JSON, or XML.

## Parameters
| Name                  | Description                                                           | Allowed Values       |  Required  | Default                                   |
|-----------------------|-----------------------------------------------------------------------|----------------------|:----------:|-------------------------------------------|
| inputFormat           | Format of the input content                                           | CSV<br/>JSON<br/>XML |    Yes     |                                           |
| outputFormat          | Format of the output content                                          | CSV<br/>JSON<br/>XML |    Yes     |                                           |
| contentIndexes        | List of content indexes to include or exclude                         | Integer              |            | empty&nbsp;- all content is considered    |
| excludeContentIndexes | Exclude specified content indexes                                     | true<br/>false       |            | false                                     |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | String               |            | empty&nbsp;- all filenames are considered |
| excludeFilePatterns   | Exclude specified file patterns                                       | true<br/>false       |            | false                                     |
| mediaTypes            | List of media types to include or exclude, supporting wildcards (*)   | String               |            | media type associated with inputFormat    |
| excludeMediaTypes     | Exclude specified media types                                         | true<br/>false       |            | false                                     |
| retainExistingContent | Retain the existing content                                           | true<br/>false       |            | false                                     |
| csvWriteHeader        | Write a header row when converting to CSV                             | true<br/>false       |            | true                                      |
| xmlRootTag            | Name of the root XML tag to use when converting to XML                | String               |            | xml                                       |
| xmlListEntryTag       | Name of the XML tag to use for list entries when converting to XML    | String               |            | listEntry                                 |

## Inputs
### Content
One or more in the format specified by inputFormat

## Outputs
### Content
Converts each content from the inputFormat to the outputFormat. The name of the new content will be the input content
name plus the file suffix associated with the outputFormat.

If retainExistingContent is true, each content will be retained and followed by its converted content.

Input content to convert may be selected (or inversely selected using the exclude parameters) with contentIndexes,
mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the content is passed through
unchanged.

## Errors
- On failure to convert content

## Notes
Provides a best effort conversion as there is not a reliable canonical way to convert between formats.