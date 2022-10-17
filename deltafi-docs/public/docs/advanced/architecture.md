# Architecture

## Database

Metadata about DeltaFiles and configuration is stored in mongodb.
The DeltaFile collection is updated each time a DeltaFile is created, queued for Action,
has an Action completed, or reaches a new stage.

## Content Storage

DeltaFile content is stored on disk using minio.
Minio is a distributed storage solution that implements an S3 interface. 

## Core

The Core is responsible for directing the DeltaFile through the system.
It maintains each DeltaFile's state and dispatches messages to Actions that should be performed.
The Core is also responsible for maintenance tasks such as aging off old DeltaFiles and requeuing
events if an Action failed to respond.

The Core is implemented as a Spring Boot project using Netflix's DGS framework.

## Core Ingress

Core ingress is the front door to DeltaFi. It creates the original DeltaFile and registers it with the Core.
Ingress is always handled as a transaction to guarantee that content and metadata have been written to
disk before reporting success to the sender.

Ingress is implemented using the Quarkus framework.

## Actions

Actions run customer business logic to transform DeltaFiles.
They listen on queues for messages from the Core, and can retrieve and store Content for each DeltaFile they process.
Some Actions can write Domain and Enrichment data.

Actions are implemented with the Spring Boot framework.

## Message Bus

Communications between the Core and Actions occurs on queues implemented through Redis stateful sets.
The goal of the message bus is to provide deliver-exactly-once semantics.

## Domains and Enrichment

Domain and Enrichment data is treated by the DeltaFi framework as opaque blocks of data attached to each DeltaFile.
It is a plugin's responsibility to enforce data standards.