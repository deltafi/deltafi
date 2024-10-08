# XmlEditor
Transforms XML content.

## Parameters
| Name                  | Description                                                           | Allowed Values                                                                                  | Required | Default                                   |
|-----------------------|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|:--------:|-------------------------------------------|
| xmlEditingCommands    | List of XML editing commands to be applied in order                   | String&nbsp;-&nbsp;See [Commands](/core-actions/org.deltafi.core.action.xml.XmlEditor#commands) |   Yes    |                                           |
| contentIndexes        | List of content indexes to include or exclude                         | Integer                                                                                         |          | empty&nbsp;- all content is considered    |
| excludeContentIndexes | Exclude specified content indexes                                     | true<br/>false                                                                                  |          | false                                     |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | String                                                                                          |          | empty&nbsp;- all filenames are considered |
| excludeFilePatterns   | Exclude specified file patterns                                       | true<br/>false                                                                                  |          | false                                     |
| mediaTypes            | List of media types to include or exclude, supporting wildcards (*)   | String                                                                                          |          | media type associated with inputFormat    |
| excludeMediaTypes     | Exclude specified media types                                         | true<br/>false                                                                                  |          | false                                     |

### Commands
XML editing commands consist of a command followed by a space-separated list of arguments.

| Command           | Arguments                                                                  | Description                                       |
|-------------------|----------------------------------------------------------------------------|---------------------------------------------------|
| removeTag         | `<xpath>`                                                                  | Removes a tag                                     |
| renameTag         | `<xpath> <new tag name>`                                                   | Renames a tag                                     |
| replaceTag        | `<xpath> <new content>`                                                    | Replaces a tag                                    |
| replaceTagContent | `<xpath> <new content>`                                                    | Replaces the contents of a tag                    |
| appendChild       | `<xpath> <new content>`                                                    | Adds content to the end of a tag's children       |
| prependChild      | `<xpath> <new content>`                                                    | Adds content to the beginning of a tag's children |
| filterOnTag       | `[[not] <xpath>] [[and\|nand\|or\|nor\|xor\|xnor] <xpath>...] "<message>"` | Filters on existence of tags                      |
| errorOnTag        | `[[not] <xpath>] [[and\|nand\|or\|nor\|xor\|xnor] <xpath>...] "<message>"` | Errors on existence of tags                       |

`and`-&nbsp;filter or error if all are found&nbsp;&nbsp;`nand`-&nbsp;filter or error if any are not found<br/>
`or`-&nbsp;filter or error if any are found&nbsp;`nor`-&nbsp;filter or error if all are not found<br/>
`xor`-&nbsp;filter or error if an odd number are found&nbsp;`xnor`-&nbsp;filter or error if none or an even number are found

##### Examples
> Input Content: `<log><note>some info</note></log>`

| Example                                                                                  | Result                                                           |
|------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| `removeTag note`                                                                         | `<log></log>`                                                    |
| `renameTag note message`                                                                 | `<log><message>some info</message></log>`                        |
| `replaceTag note <message>data</message>`                                                | `<log><message>data</message></log>`                             |
| `replaceTagContent note <message>data</message>`                                         | `<log><note><message>data</message></note></log>`                |
| `appendChild log <note>more data</note>`                                                 | `<log><note>some info</note><note>more data</note></log>`        |
| `prependChild log <note>more data</note>`                                                | `<log><note>more data</note><note>some information</note></log>` |
| `filterOnTag /log/note "Contained a bad tag!"`                                           | filters if content contains a /log/note                          |
| `errorOnTag not /log/checksum "Required tag doesn't exist!"`                             | errors if content doesn't contain a /log/checksum                |
| `filterOnTag or /log/note /log/alpha /log/bravo "Contained one or more forbidden tags!"` | filters if content contains any of the supplied tags             |
| `errorOnTag nor /log/note /log/alpha /log/bravo "Didn't contain all required tags!"`     | errors if content doesn't contain all of the supplied tags       |

## Inputs
### Content
One or more in XML format

## Outputs
### Content
Transforms each content using the provided list of XML editing commands. The content will be passed through unchanged if
commands don't match any of its tags.

Input content to transform may be selected (or inversely selected using the exclude parameters) with contentIndexes,
mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the content is passed through
unchanged.

## Errors
- On malformed command provided in xmlEditingCommands
- On XSLT transform failure
- On successful xpath matching when using the errorOnTag command
