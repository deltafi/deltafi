# ModifyMetadata
Adds, modifies, copies, or removes metadata.

## Parameters
| Name                | Description                                                                                                                                        | Allowed Values | Required | Default |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| addOrModifyMetadata | Key value pairs of metadata to be added or modified                                                                                                | string (map)   |          |         |
| copyMetadata        | Map of old metadata key names to new metadata key names to copy. The new metadata key names can be a comma-separated list to make multiple copies. | string (map)   |          |         |
| deleteMetadataKeys  | List of metadata keys to delete                                                                                                                    | string (list)  |          |         |

## Output
Content is passed through unchanged.
### Metadata
Metadata is added or replaced with metadata in addOrModifyMetadata.

Metadata may also be added or replaced by including copyMetadata to copy input metadata
to new names. Input metadata for each key in copyMetadata will be copied to one or more
metadata keys. If the corresponding value in copyMetadata is a comma-separated list,
multiple keys will be set to the copied value. If input metadata for a key does not
exist, it is ignored.

Input metadata with keys in deleteMetadataKeys are deleted. This will delete any that
may have been added by addOrModifyMetadata or copyMetadata.

