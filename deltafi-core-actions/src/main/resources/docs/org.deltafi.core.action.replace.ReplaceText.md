# ReplaceText
Replaces text in content using literal or regex matching.

## Parameters
| Name                  | Description                                                           | Allowed Values | Required | Default  |
|-----------------------|-----------------------------------------------------------------------|----------------|:--------:|:--------:|
| contentIndexes        | List of content indexes to include or exclude                         | integer (list) |          |          |
| contentTags           | List of content tags to include or exclude, matching any              | string (list)  |          |          |
| excludeContentIndexes | Exclude specified content indexes                                     | boolean        |          | false    |
| excludeContentTags    | Exclude specified content tags                                        | boolean        |          | false    |
| excludeFilePatterns   | Exclude specified file patterns                                       | boolean        |          | false    |
| excludeMediaTypes     | Exclude specified media types                                         | boolean        |          | false    |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | string (list)  |          |          |
| mediaTypes            | List of media types to consider, supporting wildcards (*)             | string (list)  |          | text/*   |
| regex                 | Use regex matching instead of literal text                            | boolean        |          | false    |
| replacement           | Replacement text (supports $1, $2, etc. for regex capture groups)     | string         | ✔        |          |
| replaceFirst          | Replace only the first occurrence (default replaces all)              | boolean        |          | false    |
| retainExistingContent | Retain the existing content                                           | boolean        |          | false    |
| searchValue           | Text or regex pattern to find                                         | string         | ✔        |          |

## Input
### Content
Input content to act on may be selected (or inversely selected using the exclude parameters) with
contentIndexes, mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the
content is passed through unchanged.

## Output
### Content
Replaces text in each selected content. By default, replaces all occurrences
of the search value. Set replaceFirst to true to replace only the first occurrence.

When regex is true, the searchValue is treated as a Java regular expression
and the replacement can use $1, $2, etc. to reference capture groups.

If retainExistingContent is true, each content will be retained, followed by its transformed content.

## Errors
* On invalid regex pattern
* On failure to process any content

