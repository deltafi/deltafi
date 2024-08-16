# XsltTransform
Transforms XML using XSLT.

## Parameters
| Name            | Description                                                 | Allowed Values  | Required | Default                                   |
|-----------------|-------------------------------------------------------------|-----------------|:--------:|-------------------------------------------|
| xslt            | XSLT transformation specification                           | String          |   Yes    |                                           |
| contentIndexes  | List of content indexes to consider                         | Integer         |          | empty&nbsp;- all content is considered    |
| mediaTypes      | List of media types to consider, supporting wildcards (*)   | String          |          | [*/xml]                                   |
| filePatterns    | List of file patterns to consider, supporting wildcards (*) | String          |          | empty&nbsp;- all filenames are considered |

## Inputs
### Content
One or more in XML format

## Outputs
### Content
Transforms each content using the provided XSLT transformation specification. The transformed content will have the same
name as the input content and the media type will be application/xml.

Input content to transform may be specified using contentIndexes, mediaTypes, and/or filePatterns. If any of these are
set and the content is not matched, the content is passed through unchanged.

## Errors
- On invalid XSLT specification provided
- On failure to transform any content
