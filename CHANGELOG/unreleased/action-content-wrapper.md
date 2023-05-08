# Changes on branch `action-content-wrapper`
Document any changes on this branch here.
### Added
- 

### Changed
- The interfaces for loading and saving Content in the Java Action Kit have been reworked.

To retrieve content as a byte array, string, or from a stream:

```java
byte[] byteArray = content.loadBytes();
String string = content.loadString();
String encodedString = content.loadString(Charset.forName(encoding));
InputStream inputStream = content.loadInputStream();
```

To store and add content and add to a Result:

```java
// create a result of the type appropriate for your Action
TransformResult transformResult = new TransformResult(context);
transformResult.saveContent(byteArray, fileName, MediaType.APPLICATION_JSON);
transformResult.saveContent(inputStream, fileName, MediaType.APPLICATION_JSON);

// you can reuse existing content and add it to the Result without having to save new Content to disk:
List<ActionContent> existingContentList = input.getContentList();
transformResult.addContent(existingContentList);

// you can also manipulate existing content
ActionContent copyOfFirstContent = existingContentList.get(0).copy();
copyOfFirstContent.setName("new-name.txt");

// get the first 50 bytes
ActionContent partOfSecondContent = existingContentList.get(1).subcontent(0, 50);
copyOfFirstContent.append(partOfSecondContent);

// store the pointers to the stitched-together content without writing to disk
transformResult.addContent(copyOfFirstContent);
```

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- 
