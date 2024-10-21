# DeleteContent
Deletes content.

## Parameters
| Name                  | Description                                                           | Allowed Values | Required | Default                                   |
|-----------------------|-----------------------------------------------------------------------|----------------|:--------:|-------------------------------------------|
| deleteAllContent      | Delete all content                                                    | true<br/>false |          | false                                     |
| contentIndexes        | List of content indexes to include or exclude                         | Integer        |          | empty&nbsp;- all content is considered    |
| excludeContentIndexes | Exclude specified content indexes                                     | true<br/>false |          | false                                     |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | String         |          | empty&nbsp;- all filenames are considered |
| excludeFilePatterns   | Exclude specified file patterns                                       | true<br/>false |          | false                                     |
| mediaTypes            | List of media types to include or exclude, supporting wildcards (*)   | String         |          | media type associated with inputFormat    |
| excludeMediaTypes     | Exclude specified media types                                         | true<br/>false |          | false                                     |

## Inputs
### Content
Any

## Outputs
### Content
All input content is deleted if the deleteAllContent parameter is true.

Input content to delete may be selected (or inversely selected using the exclude parameters) with contentIndexes,
mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the content is passed through
unchanged.
