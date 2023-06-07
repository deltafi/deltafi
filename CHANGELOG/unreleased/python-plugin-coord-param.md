# Changes on branch `python-plugin-coord-param`
Document any changes on this branch here.
### Added
- Added the option to have python plugins pass their coordinates into the plugin init method
- Added the option to have python plugins specify the package where actions can be found and loaded
- Added Makefile support to the cluster command for building plugin images
- Added a new `cluster expose` command that exposes the services necessary to run a plugin outside the cluster
- Added a new `cluster plugin run` command that will run the plugin outside the cluster

### Changed
- 

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
- The python plugin method has changed, the description is now the first argument followed by optional arguments.
  ```python
  # Before
   Plugin([
    HelloWorldDomainAction,
    HelloWorldEgressAction,
    HelloWorldEnrichAction,
    HelloWorldFormatAction,
    HelloWorldLoadAction,
    HelloWorldLoadManyAction,
    HelloWorldTransformAction,
    HelloWorldValidateAction],
    "Proof of concept for Python plugins").run()
  
  # After (using action_package instead specifying the action list
  Plugin("Proof of concept for Python plugins",  action_package="deltafi_python_poc.actions").run() 
  ```
