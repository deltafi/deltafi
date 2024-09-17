# XsltTransform
Transforms XML using XSLT.

## Parameters
| Name                  | Description                                                           | Allowed Values | Required | Default                                   |
|-----------------------|-----------------------------------------------------------------------|----------------|:--------:|-------------------------------------------|
| xslt                  | XSLT transformation specification                                     | String         |   Yes    |                                           |
| contentIndexes        | List of content indexes to include or exclude                         | Integer        |          | empty&nbsp;- all content is considered    |
| excludeContentIndexes | Exclude specified content indexes                                     | true<br/>false |          | false                                     |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | String         |          | empty&nbsp;- all filenames are considered |
| excludeFilePatterns   | Exclude specified file patterns                                       | true<br/>false |          | false                                     |
| mediaTypes            | List of media types to include or exclude, supporting wildcards (*)   | String         |          | media type associated with inputFormat    |
| excludeMediaTypes     | Exclude specified media types                                         | true<br/>false |          | false                                     |

## Inputs
### Content
One or more in XML format

## Outputs
### Content
Transforms each content using the provided XSLT transformation specification. The transformed content will have the same
name as the input content and the media type will be application/xml.

Input content to transform may be selected (or inversely selected using the exclude parameters) with contentIndexes,
mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the content is passed through
unchanged.

## Errors
- On invalid XSLT specification provided
- On failure to transform any content
