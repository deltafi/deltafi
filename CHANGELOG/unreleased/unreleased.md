### Added
- Experimental DeltaFile cache feature. By default, this is turned off with the deltaFileCache.enabled feature flag.
Flip to true to test it. To see if it is working, try processing some files while watching `deltafi redis-watch | grep BZPOPMIN`.
When enabled you should see delivery to many different topics, one for each core and worker that is running.
With it off, all messages will be delivered to the dgs topic. When on, DeltaFiles will be cached locally and made eventually
consistent in the database. This decreases processing latency but does not give a realtime view of the state in the UI.
- DeltaFile metrics for total/in flight files and bytes
- DeltaFile metrics graphs added to system summary dashboard
- Added `/blackhole` endpoint to `deltafi-egress-sink`.  The endpoint will always return a 200 and never write content to disk, acting as a noop egress destination.  The endpoint will take a latency parameter to add latency to the post response (i.e. `/blackhole?latency=0.1` to add 100ms latency)
- MergeContentJoinAction that merges content by binary concatenation, TAR, ZIP, AR, TAR.GZ, or TAR.XZ.
- Added ingress, survey, and error documentation
- Resume policies are examined when an action ERROR occurs to see if the action can be automatically scheduled for resume
- New Resume Policy permissions
  - `ResumePolicyCreate` - allows user to create a auto-resume policy
  - `ResumePolicyRead` - allows user to view auto-resume policies
  - `ResumePolicyUpdate` - allows user to edit a auto-resume policy
  - `ResumePolicyDelete` - allows user to remove a auto-resume policy
- New `autoResumeCheckFrequency` system property to control how often the auto-resume task runs.
- Added `nextAutoResume` timestmap to DeltaFile
- Added auto-resume documentation
- New properties for plugin deployments
  - `plugins.deployTimeout` - controls how long to wait for a plugin to successfully deploy
  - `plugins.autoRollback` - when true, rollback deployments that do not succeed prior to the deployTimeout elapsing
- Added `maxErrors` property to ingress flows. By default this is set to 0. If set to a number greater than 0,
ingress for a flow with at least that many unacknowledged errors will be blocked. This is based on a cached value,
so ingress cutoffs will not be exact, meaning more errors than what has been configured can accumulate before ingress
is stopped.
- Added support for configuring the number of threads per Java action type via properties. To specify the thread count
for an action type, include a section like the following in your application.yaml file:
```yaml
actions:
  actionThreads:
    org.deltafi.core.action.FilterEgressAction: 2
```
- Join action support to the Python DeltaFi Action Kit

### Changed
- Updated the load-plans command to take plugin coordinates as an argument.
- Updated system snapshots to include new `autoResumeCheckFrequency` property and auto-resume policies
- Plugin installations will wait until the deployment has rolled out successfully
- Failed plugin installations will now return related events and logs in the list of errors
- Create the child dids in the action-kit for LoadManyResults so they can be used in the load action
- Changed location for plugin running file to /tmp directory to allow running outside of docker for testing purposes
- Join actions can now return ErrorResult and FilterResult

### Removed
- Removed `nextExecution` timestamp from Action; no migration required since it had not been used previously
- Removed blackhole pod, which was superceded by the egress-sink blackhole endpoint

### Fixed
- Improved bounce and plugin restart performance in `cluster`
- A metric bug with illegal characters in tags
- DeltaFiles in the JOINING stage are now considered "in-flight" when getting DeltaFile stats.
- Replaced deprecated GitLab CI variables
- Fixed unknown enum startup errors when rolling back DeltaFi
- Fix memory leak in ingress when using deltaFileCache
- Fix deltaFileCache race condition where querying from the UI could cause the cache to repopulate from the database and lose state

### Tech-Debt/Refactor
- More precise calculation of referencedBytes and totalBytes - remove assumption that segments are contiguous.
- Perform batched delete updates in a bulk operation.
- Ingress routing rules cache did not clear when restoring a snapshot with no rules
- Fix the check to determine if a plugin-uninstall was successful
- Services that were created for a plugin are removed when the plugin is uninstalled
- Refactored StateMachine

### Upgrade and Migration
- With the change to the plugin running location, all plugins will need to be rebuilt using this DeltaFi core so that 
the application and plugins can run successfully

