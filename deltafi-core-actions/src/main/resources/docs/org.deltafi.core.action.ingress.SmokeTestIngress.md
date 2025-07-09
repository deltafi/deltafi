# SmokeTestIngress
Creates smoke test DeltaFiles.

## Parameters
| Name                   | Description                                                                                                                                     | Allowed Values | Required | Default           |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-----------------:|
| content                | The content to attach to the DeltaFile. If null, random data of size contentSize will be added to the deltaFile                                 | string         |          |                   |
| contentSize            | The size in bytes of the random content to attach to the DeltaFile. Ignored if content is set                                                   | integer        |          | 500               |
| delayChance            | An artificial delay will be randomly introduced one in every X times. Set to 0 to never delay or 1 to always delay                              | integer        |          | 0                 |
| delayMS                | Amount of time to delay if delayed                                                                                                              | integer        |          | 0                 |
| mediaType              | The content's mediaType. If null, the default is application\/text.                                                                             | string         |          | application\/text |
| metadata               | Metadata to add to each smoke-generated DeltaFile                                                                                               | string (map)   |          |                   |
| triggerImmediateChance | The next deltaFile will be immediately triggered one in every X times. Set to 0 to never immediately trigger or 1 to always immediately trigger | integer        |          | 0                 |

