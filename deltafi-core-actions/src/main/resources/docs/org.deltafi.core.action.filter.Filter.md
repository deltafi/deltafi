# Filter
Filters by default or when optional criteria is met in content or metadata.

## Parameters
| Name              | Description                                                          | Allowed Values       | Required | Default |
|-------------------|----------------------------------------------------------------------|----------------------|:--------:|:-------:|
| filterBehavior    | Filter if any, all, or no expression(s) match                        | ALL<br/>ANY<br/>NONE |          | ANY     |
| filterExpressions | List of Spring Expression Language (SpEL) expressions used to filter | string (list)        |          |         |

## Input
### Metadata
If filterExpressions is configured, input metadata may be used to check filter
conditions.

## Output
### Content
Input content is passed through unchanged when not filtered.

## Filters
* On filterExpressions not set
* On filterExpressions set and ANY, ALL, or NONE matching

## Details
### Example SpEL expressions:
Filter if no content is JSON
```
!content.stream().anyMatch(c -> c.getMediaType.equals('application/json'))
```

Filter if metadata key 'x' is set to 'y'
```
metadata['x'] == 'y'
```

Filter if metadata key 'x' is not 'y' or is not present
```
metadata['x'] != 'y' || !metadata.containsKey('x')
```
