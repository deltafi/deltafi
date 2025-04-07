# TestAction
The description

## Parameters
| Name             | Description                                      | Allowed Values | Required | Default       |
|------------------|--------------------------------------------------|----------------|:--------:|:-------------:|
| booleanParameter | No description                                   | boolean        |          | true          |
| enumParameter    | No description                                   | A<br/>B<br/>C  |          |               |
| formatParameter  | Format to compress to                            | tar<br/>zip    | ✔        |               |
| listParameter    | my great list                                    | string (list)  | ✔        |               |
| mapParameter     | Key value pairs to be added                      | integer (map)  |          |               |
| parameter        | my great property                                | string         |          | defaultString |
| tags             | A list of tags to assign to the fetched content. | string (list)  |          |               |

## Input
### Content
| Index | Name      | Media Type           | Description           |
|-------|-----------|----------------------|-----------------------|
| 0     | Content 1 | Content 1 media type | Content 1 description |
| 1     | Content 2 | Content 2 media type | Content 2 description |

### Metadata
| Key            | Description            |
|----------------|------------------------|
| Metadata 1 key | Metadata 1 description |
| Metadata 2 key | Metadata 2 description |

## Output
### Content
| Index | Name      | Media Type           | Description           |
|-------|-----------|----------------------|-----------------------|
| 0     | Content 1 | Content 1 media type | Content 1 description |
| 1     | Content 2 | Content 2 media type | Content 2 description |

### Metadata
| Key            | Description            |
|----------------|------------------------|
| Metadata 1 key | Metadata 1 description |
| Metadata 2 key | Metadata 2 description |

### Annotations
| Key              | Description              |
|------------------|--------------------------|
| Annotation 1 key | Annotation 1 description |
| Annotation 2 key | Annotation 2 description |

## Filters
* Filter 1
    * Condition A
    * Condition B
* Filter 2
    * Condition C
    * Condition D

## Errors
* Error 1
    * Condition A
    * Condition B
* Error 2
    * Condition C
    * Condition D

## Notes
* Note 1
* Note 2

## Details
The details
