# TestAction
`TRANSFORM`  ·  `org.deltafi.core.action.test.TestAction`

The description

## Parameters

| Name               | Type                   | Req | Default         | Description                                      |
|--------------------|------------------------|:---:|-----------------|--------------------------------------------------|
| `booleanParameter` | `boolean`              |     | `true`          |                                                  |
| `enumParameter`    | `string (A \| B \| C)` |     |                 |                                                  |
| `formatParameter`  | `string (tar \| zip)`  | ✔   |                 | Format to compress to                            |
| `listParameter`    | `[string]`             | ✔   |                 | my great list                                    |
| `mapParameter`     | `{string: integer}`    |     |                 | Key value pairs to be added                      |
| `parameter`        | `string`               |     | `defaultString` | my great property                                |
| `tags`             | `[string]`             |     |                 | A list of tags to assign to the fetched content. |

## Input

### Content

| # | Name      | Media Type             | Description           |
|---|-----------|------------------------|-----------------------|
| 0 | Content 1 |                        | Content 1 description |
| 1 | Content 2 | `Content 2 media type` |                       |

### Metadata

| Key              | Description            |
|------------------|------------------------|
| `Metadata 1 key` | Metadata 1 description |
|                  | Metadata 2 description |

## Output

### Content

| # | Name      | Media Type             | Description           |
|---|-----------|------------------------|-----------------------|
| 0 |           | `Content 1 media type` | Content 1 description |
| 1 | Content 2 | `Content 2 media type` | Content 2 description |

### Metadata

| Key              | Description            |
|------------------|------------------------|
| `Metadata 1 key` | Metadata 1 description |
| `Metadata 2 key` | Metadata 2 description |

### Annotations

| Key                | Description              |
|--------------------|--------------------------|
| `Annotation 1 key` | Annotation 1 description |
| `Annotation 2 key` | Annotation 2 description |

## Filters

- Filter 1
    - Condition A
    - Condition B
- unspecified
    - Condition C

## Errors

- Error 1
    - Condition A
    - Condition B
- Error 2
    - Condition C
    - Condition D

## Notes

- Note 1
- Note 2

## Details

The details
