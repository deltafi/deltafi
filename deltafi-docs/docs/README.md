# Introduction to DeltaFi

A data transformation, normalization, and enrichment platform that handles all the details,
so that you can focus on your business logic.

## The Problem Space

Modern organizations collect and send streams of data to and from many partners, sensors, and other data sources.
The one constant with these flows is change. It's a struggle to keep up with this flux and the
operational complexity that comes with it.

## How does DeltaFi help?

DeltaFi is a data-first platform that hosts your business logic and data model.
It processes data in flight while providing data stewards unprecedented control and insight into every data flow.

Users can examine the data and accompanying metadata at each point in the system to understand how it was transformed.

When something goes wrong, DeltaFi allows you to see the details of the error, correct the error conditions,
and rerun the affected data.

Engineers can focus on the core mechanics of data transformation and quickly create value through a configuration change
or a few lines of simple code.

## Features

| Feature                 | Description                                                                                                                                                                                                               |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Cloud Ready             | DeltaFi can be installed on any modern Kubernetes cluster via Helm Charts. Most DeltaFi components scale horizontally with minimal configuration tweaks.                                                                  |
| Data Loss Protection    | DeltaFi provides strong guarantees against data loss or orphaned data and metadata.                                                                                                                                       |
| Metrics and Monitoring  | Metrics are collected at the system, flow, Action, and data item level. Users can quickly identify and address bottlenecks and hot spots. Users can dig into centralized logs to uncover the root cause of any problems.  |
| Tracking and Provenance | Every data item is tracked as it traverses the system. Users can see a complete graph of its journey.                                                                                                                     |
| Error Reporting         | Users can customize the format and delivery of error reports that result from any processing errors.                                                                                                                      |
| Error Handling          | If data processing fails because of a transient problem, users can resume processing at any time from the point that it originally failed, or replay data from the beginning.                                             |
| Retention               | Users can create policies dictating how long processed data should be retained.                                                                                                                                           |
| Filtering               | Users can terminate unwanted data mid-flow.                                                                                                                                                                               |
| Reinjection             | Data can be split into multiple children that can be injected into the front of the system for independent processing.                                                                                                    |
