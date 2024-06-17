# XmlEditor

The XmlEditor implements a core action to effect simple XML transformations.

The action accepts parameters which include one or more commands to act on XML content, which are applied in the order
in which they are specified.  The parameters also include optional filters to select or ignore content based on media
type, file name pattern, and content index.

A command is provided as a string expression, consisting of a command name then one or more arguments.  The command name
and arguments may be separated by either (1) one or more spaces or (2) a comma and zero or more spaces.

The XmlEditor defines two types of operations that may be invoked by the commands:

| Operation Type   | Description                                                                                                                                                                                                                     | Example Use Case                                                                                                                                                                                                                                                   |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Modify Operation | An operation that may (or may not) modify XML content, producing a TransformResult with the content (either modified or unmodified) and return a TransformResult.  If an error occurs, then an ErrorResult is returned instead. | <br>- If the XML content DOES contain tag name `x`, then rename the tag to `y` and return a TransformResult.  <br>- If the XML content does NOT contain tag name `x`, then allow the content to pass unmodified and return a TransformResult.                      |
| Screen Operation | An operation that produces a FilterResult or ErrorResult else leaves the content unmodified and returns a TransformResult.  If an error occurs, then an ErrorResult is returned.                                                | <br>- If the XML content DOES contain tag name `x`, then stop the content by filtering it (e.g., return a FilterResult or ErrorResult).  <br>- If the content does NOT contain the tag, then allow the content to pass unmodified (e.g. return a TransformResult). |

## Modify Operations

A *Modify Operation* is an operation intended to (possibly) change XML content.  The operation produces a TransformResult
containing content that may or may not be modified.  If an error occurs, then an ErrorResult is returned. 

For the command descriptions below, consider an input XML content of `<log><note>some information</note></log>`.

| Operation                                      | Command                                            | Example                                                                                                                                                  |
|------------------------------------------------|----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Remove a tag                                   | `removeTag <search pattern>`                       | Given the command `removeTag note`, return modified content of `<log></log>`                                                                             |
| Rename a tag                                   | `renameTag <search pattern> <new tag name>`        | Given the command `renameTag note message`, return modified content of `<log><message>some information</message></log>`                                  |
| Replace a tag and its content with new content | `replaceTag <search pattern> <new content>`        | Given the command `replaceTag note <message>data</message>`, return modified content of `<log><message>data</message></log>`                             |
| Replace a tag's content with new content       | `replaceTagContent <search pattern> <new content>` | Given the command `replaceTagContent note <message>data</message>`, return modified content of `<log><note><message>data</message></note></log>`         |
| Append child                                   | `appendChild <search pattern> <new content>`       | Given the command `appendChild log <note>more data</note>`, return modified content of `<log><note>some information</note><note>more data</note></log>`  |
| Prepend child                                  | `prependChild <search pattern> <new content>`      | Given the command `prependChild log <note>more data</note>`, return modified content of `<log><note>more data</note><note>some information</note></log>` |

The `search pattern` is a valid XSLT search pattern.  A search pattern of `log` and `/log` will both match the root node
"log".  A search pattern of `note` will match all nodes with "note":  "log"'s child "note" in this case, but if the
XML document contained other tags with "node" at different points in the document, then those would be matched as well.
To match only "log's" child "note", then use a search pattern of `/log/note`.

## Screen Operations

A *Screen Operation* is an operation intended to stop XML content from continuing to process or allow it to continue.
The operation produces a FilterResult or ErrorResult, else the content is unmodified and a TransformResult is returned.
If an error occurs, then an ErrorResult is returned.

| Operation                                                    | Command                                                                                                   | Example                                                                                                                        |
|--------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| Filter/error if a tag DOES exist                             | `filterOnTag\|errorOnTag <search xpath> "<message>"`                                                      | Given the command `filterTag /log/note "A known-bad tag!"`, return a FilterResult                                              |
| Filter/error if a tag does NOT exist                         | `filterOnTag\|errorOnTag not <search xpath> "<message>"`                                                  | Given the command `errorTag not /log/checksum "Required tag doesn't exist!"`, return an ErrorResult                            |
| Filter/error if the expression on two or more tags is 'true' | `filterOnTag\|errorOnTag and\|nand\|or\|nor\|xor\|xnor <search xpath 1> ... <search xpath n> "<message>"` | Given the command `errorTag or /log/note /log/alpha /log/bravo "Contained one or more forbidden tags!"`, return an ErrorResult |

The `search xpath` is a valid XPath expression.  A search xpath of `log` and `/log` will both match the root node "log".
However, a search xpath of `note` will have no matches.  In this example, the node "note" would be matched with
`/log/note`.

The logic operators (e.g., `and`, `or`, etc.) are described in section [Logic Operators](#logic-operators).

The `message` must have length greater than two characters.

## Logic Operators

### AND

| Search XPath 1 | Search XPath 2 | Result |
|----------------|----------------|--------|
| not found      | not found      | false  |
| not found      | found          | false  |
| found          | not found      | false  |
| found          | found          | true   |

### NAND

*NAND* provides an inverted "and" (e.g., inverted *AND*) logic function.

| Search XPath 1 | Search XPath 2 | Result |
|----------------|----------------|--------|
| not found      | not found      | true   |
| not found      | found          | true   |
| found          | not found      | true   |
| found          | found          | false  |

### OR

*OR* provides an inclusive "or" logic function.

| Search XPath 1 | Search XPath 2 | Result |
|----------------|----------------|--------|
| not found      | not found      | false  |
| not found      | found          | true   |
| found          | not found      | true   |
| found          | found          | true   |

### NOR

*NOR* provides an inverted inclusive "or" (e.g., inverted *OR*) logic function.

| Search XPath 1 | Search XPath 2 | Result |
|----------------|----------------|--------|
| not found      | not found      | true   |
| not found      | found          | false  |
| found          | not found      | false  |
| found          | found          | false  |

### XOR

*XOR* provides an "either/or" logic function.

| Search XPath 1 | Search XPath 2 | Result |
|----------------|----------------|--------|
| not found      | not found      | false  |
| not found      | found          | true   |
| found          | not found      | true   |
| found          | found          | false  |

### XNOR

*XNOR* provides an inverted "either/or" (e.g., inverted *XOR*) logic function.

| Search XPath 1 | Search XPath 2 | Result |
|----------------|----------------|--------|
| not found      | not found      | true   |
| not found      | found          | false  |
| found          | not found      | false  |
| found          | found          | true   |




