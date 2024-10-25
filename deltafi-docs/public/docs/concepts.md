# Concepts

## DeltaFile

A DeltaFile is created when data is ingested into the DeltaFi system. It represents and tracks the processing of a
single unit of data through the system. The DeltaFile contains metadata about the data, references to the actual
content, and a history of the actions performed on it.

## Data Sources

Data Sources are the entry points for data into the DeltaFi system. They create DeltaFiles and publish them to topics
for further processing.

* REST Data Sources allow external systems to push data into DeltaFi via HTTP requests.
* Timed Data Sources periodically generate or fetch data based on a defined schedule.

## Transforms

Transforms subscribe to topics, process the DeltaFiles they receive, and publish the results to other topics.
They contain a list of Transform Actions that execute sequentially.

## Data Sinks

Data Sinks subscribe to topics and send the processed data out of the DeltaFi system. They contain a single Egress
Action that defines how the data is sent to external systems.

## Actions

Actions are units of code that perform operations on DeltaFiles. They may add or modify content, update metadata, or
perform other processing tasks. The responses they return are used to update the DeltaFiles.

### Timed Ingress Actions

Timed Ingress Actions are used in Timed Data Sources to periodically generate or fetch data and create DeltaFiles.

### Transform Actions

Transform Actions modify the content or metadata of a DeltaFile. They can create new content, modify existing content,
update metadata, or perform any other transformation on the data.

### Egress Actions

Egress Actions define how processed data is sent out of the DeltaFi system. They handle the actual transmission of data
to external systems or storage.

## Publish-Subscribe Pattern

DeltaFi uses a publish-subscribe pattern to move DeltaFiles between different components of the system. Data Sources and
Transforms can publish DeltaFiles to topics, while Transforms and Data Sinks can subscribe to these topics
to receive DeltaFiles for processing.

## Plugins

Plugins are collections of Actions and optional Flows that incorporate those Actions. They allow for easy extension of
DeltaFi's capabilities.

DeltaFi ships with a collection of useful Plugins. The DeltaFi Action Kit (provided in both Java and Python) is used to
create Actions for plugins. Guides and starters are provided to make it easy to build your own plugins.

## Variables

Variables allow for runtime configuration of Flows and Actions. They can be defined at the plugin level and used across
multiple Flows and Actions within that plugin.

## Join Configuration

Join Configuration allows Transform Actions to process multiple DeltaFiles together. It defines criteria for collecting
a batch of DeltaFiles before executing the Transform Action.