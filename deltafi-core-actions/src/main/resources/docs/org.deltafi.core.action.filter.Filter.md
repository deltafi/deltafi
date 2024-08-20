# Filter
Filters by default or when optional criteria is met in content or metadata.

## Parameters
| Name               | Description                                                          | Allowed Values        | Required | Default                  |
|--------------------|----------------------------------------------------------------------|-----------------------|:--------:|--------------------------|
| filterExpressions  | List of Spring Expression Language (SpEL) expressions used to filter | String                |          | empty&nbsp;- filters all |
| filterBehavior     | Filter if any, all, or no expression(s) match                        | ANY<br/>ALL<br/>NONE  |          | ANY                      |

## Inputs
### Content
Any

### Metadata
If filterExpressions is configured, input metadata may be used to check filter conditions.

## Outputs
### Content
Input content is passed through unchanged when not filtered.

## Errors
None

## Notes
If filtered, the entry for this action in the DeltaFile will be placed in the terminal FILTERED state.

### Example SpEL expressions:
Filter if no content is JSON
```
!content.stream().anyMatch(c -> c.getMediaType.equals('application/json')
```
\
Filter if metadata key 'x' is set to 'y'
```
metadata['x'] == 'y'
```
\
Filter if metadata key 'x' is not 'y' or is not present
```
metadata['x'] != 'y' || !metadata.containsKey('x')
```
