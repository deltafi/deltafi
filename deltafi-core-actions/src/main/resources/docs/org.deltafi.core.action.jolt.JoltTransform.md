# JoltTransform
Transforms JSON content using Jolt.

## Parameters
| Name           | Description                                                 | Allowed Values | Required | Default                                   |
|----------------|-------------------------------------------------------------|----------------|:--------:|-------------------------------------------|
| joltSpec       | Jolt transformation specification provided as a JSON string | String         |   Yes    |                                           |
| contentIndexes | List of content indexes to consider                         | Integer        |          | empty&nbsp;- all content is considered    |
| mediaTypes     | List of media types to consider, supporting wildcards (*)   | String         |          | [application/json]                        |
| filePatterns   | List of file patterns to consider, supporting wildcards (*) | String         |          | empty&nbsp;- all filenames are considered |

## Inputs
### Content
One or more in JSON format

## Outputs
### Content
Transforms each content using a Jolt transformation with the provided Jolt specification. The transformed content will
have the same name as the input content and the media type will be application/json.

Input content to transform may be specified using contentIndexes, mediaTypes, and/or filePatterns. If any of these are
set and the content is not matched, the content is passed through unchanged.

## Errors
- On invalid Jolt specification provided
- On failure to transform any content
