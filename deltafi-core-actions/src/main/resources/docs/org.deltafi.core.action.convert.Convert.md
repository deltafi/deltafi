# Convert
Converts content between CSV, JSON, or XML.

## Parameters
| Name                  | Description                                                           | Allowed Values       | Required | Default   |
|-----------------------|-----------------------------------------------------------------------|----------------------|:--------:|:---------:|
| contentIndexes        | List of content indexes to include or exclude                         | integer (list)       |          |           |
| contentTags           | List of content tags to include or exclude, matching any              | string (list)        |          |           |
| csvWriteHeader        | Write a header row when converting to CSV                             | boolean              |          | true      |
| excludeContentIndexes | Exclude specified content indexes                                     | boolean              |          | false     |
| excludeContentTags    | Exclude specified content tags                                        | boolean              |          | false     |
| excludeFilePatterns   | Exclude specified file patterns                                       | boolean              |          | false     |
| excludeMediaTypes     | Exclude specified media types                                         | boolean              |          | false     |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | string (list)        |          |           |
| inputFormat           | Format of the input content                                           | CSV<br/>JSON<br/>XML | ✔        |           |
| mediaTypes            | List of media types to include or exclude, supporting wildcards (*)   | string (list)        |          |           |
| outputFormat          | Format of the output content                                          | CSV<br/>JSON<br/>XML | ✔        |           |
| retainExistingContent | Retain the existing content                                           | boolean              |          | false     |
| xmlListEntryTag       | Name of the XML tag to use for list entries when converting to XML    | string               |          | listEntry |
| xmlRootTag            | Name of the root XML tag to use when converting to XML                | string               |          | xml       |

## Input
### Content
Input content to act on may be selected (or inversely selected using the exclude parameters) with
contentIndexes, mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the
content is passed through unchanged.

## Output
### Content
Converts each content from the inputFormat to the outputFormat. The name of the new
content will be the input content name plus the file suffix associated with the
outputFormat.

If retainExistingContent is true, each content will be retained, followed by its transformed content.

## Errors
* On failure to convert content

## Notes
* Provides a best effort conversion as there isn't a reliable canonical way to convert between formats.

