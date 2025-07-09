# Merge
Merges multiple content into a single content.

## Parameters
| Name           | Description                                                                                                                                                                                                            | Allowed Values | Required | Default |
|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| mediaType      | Media type to apply to the merged file. If not set, will use the media type of the first file merged.                                                                                                                  | string         |          |         |
| mergedFilename | Name of the merged file. Embed ```{{deltaFileName}}``` to have it replaced with the deltaFileName or content[0].name to get the name of the first content. If not set, will use the filename of the first file merged. | string         |          |         |

## Output
### Content
All input content is concatenated into a single content with a name provided by
mergedFilename and a media type provided by mediaType.

