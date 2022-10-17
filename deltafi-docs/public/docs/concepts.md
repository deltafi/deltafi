# Concepts

## DeltaFiles

The *DeltaFile* is the core representation of every piece of data that enters DeltaFi.
When data is ingested into the system it is assigned a Universally Unique Identifier (UUID) and a metadata record is created.
As the DeltaFile travels through the system, each Action will accrete additional metadata on this record.
Any content that is stored or created for this data item will always be tied by UUID to the core DeltaFile record.

## Actions

DeltaFi's unit of processing is called an *Action*.
Actions are units of code that are hosted and executed by the platform to make a single focused transformation to a
DeltaFile.

DeltaFi enforces a strict sequence of Actions, so that DeltaFiles are processed in a predictable way.
Different types of Actions can transform, load, enrich, format, validate, and egress the data. 

## Flows

Actions can be strung together to create a *Flow*.

Every piece of data that enters DeltaFi is assigned to Flows based on characteristics of the data.

### Ingress Flows

Ingress Flows accept data from partners and convert it to your internal stable formats.

An Ingress Flow is a named configuration that maps a particular stream of incoming DeltaFiles into one or more Domains.
Each Ingress Flow should be supplied data of a known, homogeneous type.
The Ingress Flow defines the series of Transform Actions that will process DeltaFiles in the flow before they reach a
Load Action that loads it into to the correct Domains.

### Enrichment Flows

Enrichment Flows act on the data that was loaded into Domains in the ingress stage.

Enrichment Flows define the sets of Domain Actions and Enrich Actions that validate and enrich the Domain data. Enrich Actions can be chained together to act on data produced by other Enrich Actions. Both Domain and Enrich Actions can extract metadata which will be made searchable in the system.

### Egress Flows

Egress Flows transform your data into a format that makes sense for the downstream partners who receive it.

The Egress Flow defines the series of Format Actions, Validate Actions,
and Egress Actions that will process DeltaFiles in the flow.

Unlike with Ingress Flows, there can be multiple Egress Flows that act upon the same DeltaFile,
customizing Domain and Enrichment data for multiple consumers.

## Domains

DeltaFi hosts centralized *Domains* that define how you want to store and understand your data.
A predictable, normalized data model is at the center of every data flow.

Every DeltaFile is normalized into a Data Domain as directed by Ingress Flows.
All transformed data is pulled from Data Domains as directed by Egress Flows.

## Enrichment

*Enrichment* can be added after normalizing data into one or more Domain. Enrichment is the process of
correlating data between Domains or enriching it through external lookups or services. In DeltaFi,
the process of Enrichment can occur over multiple passes, as we learn more and more about the data.

## Plugins

Collections of Actions, Flows, Domains, and Enrichment are installed through packages called *Plugins*.

DeltaFi ships free with a collection of useful Plugins.
We provide guides and starters that make it easy to put together your own.
