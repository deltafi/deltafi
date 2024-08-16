# Merge
Merges multiple content into a single content.

## Parameters
| Name           | Description                            | Allowed Values                                                                                    | Required | Default                             |
|----------------|----------------------------------------|---------------------------------------------------------------------------------------------------|:--------:|-------------------------------------|
| mergedFilename | Name of the merged file                | String - embed ```{{filename}}``` to have it replaced with the filename of the first file merged  |          | filename of the first file merged   |
| mediaType      | Media type to apply to the merged file | String                                                                                            |          | media type of the first file merged |

## Inputs
### Content
Any

## Outputs
### Content
All input content is concatenated into a single content with a name provided by mergedFilename and a media type provided
by mediaType.

## Errors
None
