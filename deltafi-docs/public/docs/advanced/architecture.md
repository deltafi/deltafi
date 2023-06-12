# Architecture

## Database

DeltaFiles and system configuration are stored in MongoDB. The DeltaFile collection is updated each time a DeltaFile is
created, queued for Action, has an Action completed, or reaches a new stage.

## Content Storage

DeltaFile content is stored on disk using MinIO. MinIO is a distributed storage solution that implements an S3
interface. 

## Core

The Core is responsible for creating a DeltaFile for ingressed data and directing it through the system.
It maintains each DeltaFile's state, dispatches messages to Actions that should be performed, and handles responses from
the Actions.

The Core is also responsible for maintenance tasks such as aging off old DeltaFiles and requeuing events if an Action
failed to respond.

The Core is implemented as a Spring Boot project using Netflix's DGS framework.

## Actions

Actions run customer business logic on DeltaFiles. They listen on queues for messages from the Core, perform their
action, and return a response to the Core. Actions can retrieve and store content for DeltaFiles they process.

## Message Bus

Communications between the Core and Actions occurs on queues implemented through Redis stateful sets. The goal of the
message bus is to provide deliver-exactly-once semantics.

## Domains and Enrichments

A DeltaFile may contain a list of named Domains and a list of named Enrichments. Each Domain and Enrichment is a text
value.

Actions may be configured to execute only when a list of Domains or a list of Enrichments is present in the DeltaFile.