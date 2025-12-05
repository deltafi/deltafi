# Introduction to DeltaFi

A data transformation, normalization, and enrichment platform. You write the business logic; DeltaFi handles the rest.

## The Problem

Data pipelines are messy. Sources change formats without warning. When something breaks, tracing the issue is painful.

## How DeltaFi Helps

- **Full provenance** - Every piece of data is tracked through the system. See exactly how it was transformed at each step.
- **Inspect everything** - Examine data and metadata at any point in the pipeline to understand what happened.
- **Resume/replay on errors** - When something breaks, fix the problem and rerun the affected data from where it failed.
- **Code-light** - Most work is configuration. Write simple actions for your business logic, DeltaFi handles the rest.

## Getting Started

Ready to try DeltaFi? Follow the [Quick Start](/getting-started/quick-start) to install and run your first data flow in minutes.

For role-specific guidance, see:
- [Operator's Guide](/getting-started/for-operators) - Running and managing DeltaFi
- [Plugin Developer's Guide](/getting-started/for-plugin-developers) - Building custom actions
- [Core Developer's Guide](/getting-started/for-core-developers) - Contributing to DeltaFi

## Features

| Feature                 | Description                                                                                                                                                                                                               |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Cloud Ready             | DeltaFi can be installed on any modern Kubernetes cluster via Helm Charts. Most DeltaFi components scale horizontally with minimal configuration tweaks.                                                                  |
| Data Loss Protection    | DeltaFi provides strong guarantees against data loss or orphaned data and metadata.                                                                                                                                       |
| Metrics and Monitoring  | Metrics at system, flow, action, and data item level. Find and fix bottlenecks. Centralized logs for root cause analysis.  |
| Tracking and Provenance | Every data item is tracked through the system. View the complete transformation graph.                                                                                                                     |
| Error Reporting         | Users can customize the format and delivery of error reports that result from any processing errors.                                                                                                                      |
| Error Handling          | If data processing fails because of a transient problem, users can resume processing at any time from the point that it originally failed, or replay data from the beginning.                                             |
| Retention               | Users can create policies dictating how long processed data should be retained.                                                                                                                                           |
| Filtering               | Users can terminate unwanted data mid-flow.                                                                                                                                                                               |
| Reinjection             | Data can be split into multiple children that can be injected into the front of the system for independent processing.                                                                                                    |
