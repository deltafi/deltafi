# ModifyMetadata
#### <font color='#888'>Type: org.deltafi.core.action.metadata.ModifyMetadata</font>
Adds, modifies, copies, or removes metadata.

## Parameters
| Name                | Description                                                                                                                                                                                                                                                                | Allowed Values | Required | Default |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| addOrModifyMetadata | Shortcut for an ADD_OR_MODIFY operation, applied before the operations list.                                                                                                                                                                                               | string (map)   |          |         |
| copyMetadata        | Shortcut for a COPY operation (source key to comma-separated destination key(s)), applied before the operations list.                                                                                                                                                      | string (map)   |          |         |
| deleteMetadataKeys  | Shortcut for a DELETE_KEYS operation, applied before the operations list.                                                                                                                                                                                                  | string (list)  |          |         |
| operations          | Ordered metadata operations applied as a pipeline over a working copy: the first sees the input\nmetadata, each next sees the prior result. The convenience fields above run first. Types: ADD_OR_MODIFY,\nCOPY, DELETE_KEYS, DELETE_MATCHING, REPLACE_KEY, REPLACE_VALUE. | object (list)  |          |         |
### `operations`
#### ADD_OR_MODIFY
| Name      | Description                                                                                                                                    | Allowed Values | Required | Default |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| condition | Apply this operation only when true. May be a template resolving to true\/false, e.g. {{ metadata['normalize'] == 'true' }}. Defaults to true. | boolean        |          | true    |
| values    | Key\/value pairs to add or replace.                                                                                                            | string (map)   |          |         |
#### COPY
| Name      | Description                                                                                                                                    | Allowed Values | Required | Default |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| condition | Apply this operation only when true. May be a template resolving to true\/false, e.g. {{ metadata['normalize'] == 'true' }}. Defaults to true. | boolean        |          | true    |
| copy      | Map of source key to destination key(s); comma-separate for multiple. Copies the current value; missing sources are skipped.                   | string (map)   |          |         |
#### DELETE_KEYS
| Name      | Description                                                                                                                                    | Allowed Values | Required | Default |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| condition | Apply this operation only when true. May be a template resolving to true\/false, e.g. {{ metadata['normalize'] == 'true' }}. Defaults to true. | boolean        |          | true    |
| keys      | Metadata keys to delete.                                                                                                                       | string (list)  |          |         |
#### DELETE_MATCHING
| Name      | Description                                                                                                                                    | Allowed Values | Required | Default |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| condition | Apply this operation only when true. May be a template resolving to true\/false, e.g. {{ metadata['normalize'] == 'true' }}. Defaults to true. | boolean        |          | true    |
| pattern   | Regex; metadata keys whose name matches are deleted.                                                                                           | string         |          |         |
#### REPLACE_KEY
| Name         | Description                                                                                                                                    | Allowed Values | Required | Default |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| condition    | Apply this operation only when true. May be a template resolving to true\/false, e.g. {{ metadata['normalize'] == 'true' }}. Defaults to true. | boolean        |          | true    |
| dropOriginal | When true, rename (drop the original key); when false, copy (keep both). Defaults to true.                                                     | boolean        |          | true    |
| pattern      | Regex matched against each key name.                                                                                                           | string         |          |         |
| replacement  | Replacement for matched key names; supports $1, $2 capture groups.                                                                             | string         |          |         |
#### REPLACE_VALUE
| Name        | Description                                                                                                                                    | Allowed Values | Required | Default |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| condition   | Apply this operation only when true. May be a template resolving to true\/false, e.g. {{ metadata['normalize'] == 'true' }}. Defaults to true. | boolean        |          | true    |
| keyPattern  | Optional regex limiting which keys' values are rewritten; omit to apply to all.                                                                | string         |          |         |
| pattern     | Regex matched against each value.                                                                                                              | string         |          |         |
| replacement | Replacement for matched values; supports $1, $2 capture groups.                                                                                | string         |          |         |

## Output
Content is passed through unchanged.
### Metadata
Metadata is modified by applying an ordered list of operations to a working copy of
the metadata. The first operation sees the input metadata; every subsequent operation
sees the working set produced by the operation before it. Operations that read
existing metadata (COPY, REPLACE_KEY, REPLACE_VALUE, DELETE_MATCHING) therefore act on
the running state, not the original input. Each operation may carry a condition that
determines whether it is applied.

Operation types:
- ADD_OR_MODIFY: add or replace key-value pairs.
- COPY: copy a key's value to one or more new keys.
- DELETE_KEYS: delete an explicit list of keys.
- DELETE_MATCHING: delete every key whose name matches a regex.
- REPLACE_KEY: rewrite key names with a regex (rename, or copy when dropOriginal is
  false).
- REPLACE_VALUE: rewrite values with a regex, optionally limited to keys matching a
  pattern.

The addOrModifyMetadata, copyMetadata, and deleteMetadataKeys convenience parameters
are applied first (as ADD_OR_MODIFY, COPY, and DELETE_KEYS operations, in that
order) ahead of the operations list.

