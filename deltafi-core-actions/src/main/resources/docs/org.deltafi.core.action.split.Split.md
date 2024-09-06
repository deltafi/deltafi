# Split
Splits content into multiple DeltaFiles.

## Parameters
None

## Inputs
### Content
Two or more

### Metadata
Any

## Outputs
### Content
Each DeltaFile will contain a single content from the original DeltaFile.

### Metadata
Each DeltaFile will contain a copy of the metadata from the original DeltaFile.

## Errors
None

## Notes
The entry for this action in the original DeltaFile will be placed in the terminal SPLIT state.

New DeltaFiles created for each content in the original file will advance in the current flow.

## Usage
This action is typically used after a Decompress action to process each content individually after it has been extracted
from an ingested compressed file.