# Data Retention

Data stored in DeltaFi consists of content stored in MinIO and metadata stored in MongoDB.
Retention of these classes of data can be controlled through a global age-off rule as well as any number of
customizable delete policies.

Take care setting your rules - there is no supported way to recover data after it has been deleted.

## Global Age-Off

The global age-off is a fail-safe that removes all content and metadata after a set number of days.
Control the age-off with the `delete.ageOffDays` setting on the System Properties page in the GUI.

## Delete Policies

Delete policies are rules that are customizable by either per-DeltaFile data such as creation time, completion time,
flow, and number of bytes, or by system wide conditions such as free disk space.
Add, remove, and edit delete policies on the Delete Policies page of the GUI.

The System Properties page includes a few advanced settings that let you customize the overall delete policy behavior:
- `delete.frequency` - how often each delete policy executes
- `delete.policyBatchSize` - the maximum number of deltaFiles that will be removed from MinIO or MongoDB in a single bulk operation. Batches will be deleted back-to-back until all eligible DeltaFiles are deleted each `delete.frequency` cycle

If you use the reinject, loadMany, or formatMany features of DeltaFi to create child DeltaFiles, be sure to allow enough time for children to complete before deleting parents.

All delete policies added to the system must be named. Any DeltaFiles deleted by that policy will include an AUDIT log entry including the policy name.

Delete policies can be enabled or disabled on the Delete Policies page.

### Timed Delete Policies

Timed delete policies remove DeltaFiles that have exceeded a time threshold based either on the creation time or completion time.

The following additional options are available:

* delete metadata - by default, only content is deleted. Enabling this option deletes metadata as well
* flow (optional) - set the policy to only be run against a specific flow 
* minimum bytes (optional) - only apply this policy to DeltaFiles over a certain size 

### Disk Space Delete Policies

DeltaFi monitors disk space available on the node that hosts MinIO.
Configure a disk space policy to delete data when the % of disk space used exceeds a specified threshold.
The oldest completed DeltaFiles (by creation date) will be deleted first.

Disk Space Delete policies only delete content, not metadata.

The following additional options are available:

* flow (optional) - set the policy to only be run against a specific flow