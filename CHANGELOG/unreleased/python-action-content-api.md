# Changes on branch `python-action-content-api`
Document any changes on this branch here.
### Added
- 

### Changed
- The interfaces for loading and saving Content in the Python Action Kit have been reworked.

To retrieve content as bytes or a string:

```python
bytes = content.load_bytes()
string = content.load_string()
```

To store and add content and add to a Result:

```python
// create a result of the type appropriate for your Action
result = TransformResult(context)
result.save_byte_content(bytes_data, filename, 'application/json')
result.save_string_content(string_data, filename, 'application/xml')

// you can reuse existing content and add it to the Result without having to save new Content to disk:
existing_content = input.first_content()
result.add_content(existing_content)

// you can also manipulate existing content
copy = input.first_content().copy()
copy.set_name("new-name.txt")

// get the first 50 bytes of the next piece of incoming content
sub_content = input.content[1].subcontent(0, 50)
copy.append(sub_content)

// store the pointers to the stitched-together content without writing to disk
result.add_content(sub_content)
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
