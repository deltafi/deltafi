# ModifyMediaType
Modifies content media types.

## Parameters
| Name                | Description                                                                                                        | Allowed Values                                       | Required | Default |
|---------------------|--------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|:--------:|---------|
| mediaTypeMap        | Map of old to new media types, supporting wildcards (*) in the old media types                                     | key&nbsp;-&nbsp;String<br/>value&nbsp;-&nbsp;String  |          |         |
| indexMediaTypeMap   | Map of indexes to media types. Used to update the media type of specific content by index. Overrides mediaTypeMap. | key&nbsp;-&nbsp;Integer<br/>value&nbsp;-&nbsp;String |          |         |
| errorOnMissingIndex | Error if content for any index in indexMediaTypeMap is missing.                                                    | true<br/>false                                       |          | false   |
| autodetect          | Autodetect media type if not found in mediaTypeMap or indexMediaTypeMap.                                           | true<br/>false                                       |          | true    |

## Inputs
### Content
One or more

## Outputs
### Content
Input content data is passed through unchanged.

If mediaTypeMap is provided, each input content's media type is checked against the keys in this map. Keys may include
wildcards (*) to match any sequence of characters. If a match is found, the input content's media type is replaced with
the corresponding value.

If indexMediaTypeMap is provided, each input content's (zero-based) index is checked against the keys in this map. If a
match is found, the input content's media type is replaced with the corresponding value. This map takes precedence over
mediaTypeMap.

If autodetect is set to true and a media type is not found in mediaTypeMap or indexMediaTypeMap, each input content's
media type will be autodetected from its data. If the media type cannot be autodetected, the media type will pass
through unchanged.

## Errors
- On errorOnMissingIndex set to true and content for any index in indexMediaTypeMap is missing
