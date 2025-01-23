# TagContent
Adds specified tags to content based on optional filters such as indices, filename pattern, and media type.

## Parameters
| Name                   | Description                                                                                                                                                       | Allowed Values                                      | Required | Default |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|:--------:|---------|
| contentIndexes         | List of content indexes to include or exclude. If omitted, all content is included.                                                                              | List of integers                                  |          |         |
| excludeContentIndexes  | If true, the specified content indexes are excluded instead of included.                                                                                         | true<br/>false                                    |          | false   |
| filePatterns           | List of filename patterns to include or exclude, supporting wildcards (\*). If omitted, all filenames are included.                                              | List of strings                                   |          |         |
| excludeFilePatterns    | If true, the specified file patterns are excluded instead of included.                                                                                           | true<br/>false                                    |          | false   |
| mediaTypes             | List of media types to include or exclude, supporting wildcards (\*). If omitted, all media types are included.                                                   | List of strings                                   |          |         |
| excludeMediaTypes      | If true, the specified media types are excluded instead of included.                                                                                             | true<br/>false                                    |          | false   |
| tagsToAdd              | Tags to add to the content.                                                                                                                                       | List of strings                                   |    ✔     |         |

## Inputs
### Content
One or more content items.

## Outputs
### Content
All input content is passed through unchanged. Tags are added to content that matches the specified filters.

- If `contentIndexes` is provided, only content at the specified indices is included (or excluded if `excludeContentIndexes` is true).
- If `filePatterns` is provided, only content with filenames matching the patterns is included (or excluded if `excludeFilePatterns` is true).
- If `mediaTypes` is provided, only content with the specified media types is included (or excluded if `excludeMediaTypes` is true).
- The `tagsToAdd` parameter specifies the tags that will be added to matching content.

Content that does not match any filters will still pass through unchanged without any tags added.

## Errors
- If `tagsToAdd` is not specified or is empty.