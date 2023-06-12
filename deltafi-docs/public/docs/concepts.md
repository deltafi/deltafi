# Concepts

## DeltaFile

When data is ingested into the system, it is stored and a DeltaFile is created to track its processing.

The DeltaFile will be processed by Flows configured in the system starting with the ingest Flow provided in the header,
in the FlowFile, or automatically determined.

## Flows

Flows contain Actions that act on data from DeltaFiles.

If the ingest Flow is a Transform Flow, the DeltaFile will be processed by a sequential list of Transform Actions
followed by an Egress Action.

Otherwise, the DeltaFile will be processed by the ingest Ingress Flow, followed by any number of qualifying Enrich
Flows, and finally any number of qualifying Egress Flows.

### Ingress Flows

Ingress Flows transform ingressed data and add Domains to DeltaFiles. They contain a list of Transform Actions that
execute sequentially followed by a single Load Action.

### Enrich Flows

Enrich Flows add Enrichments to a DeltaFile. They contain a list of Domain Actions that execute concurrently followed by
a list of Enrich Actions that execute concurrently. Enrich Actions, however, may be chained together to act on
Enrichments produced by prior Enrich Actions.

### Egress Flows

Egress Flows format and validate content before sending it to downstream recipients. They contain a single Format
Action, followed by a list of Validate Actions that execute concurrently, and finally a single Egress Action.

## Actions

Actions are units of code that act on data from DeltaFiles. They may add or modify content. Responses they return are
used to update the DeltaFiles.

## Domains

Domains are text values representing the data model. They are created by Ingress Flows and are stored in the DeltaFile.

Enrich and Egress Flows will use the Domains to perform their Actions.

## Enrichments

Enrichments are text values representing additions to the data model. They are created by Enrich Flows and are stored in
the DeltaFile. Enrichments may be created by correlating data between Domains or through external services.

## Plugins

Plugins are collections of Actions and optional Flows that incorporate those Actions.

DeltaFi ships with a collection of useful Plugins.

The DeltaFi Action Kit (provided in both Java and Python) is used to create Actions for plugins. Guides and starters are
provided to make it easy to build your own.
