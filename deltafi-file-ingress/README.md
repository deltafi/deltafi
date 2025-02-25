# DeltaFi File Ingress

This application monitors subdirectories under a configurable root directory for the creation of new files which are ingressed to DeltaFi. Subdirectory names serve as the data source when posting to DeltaFi. For instance, the configuration could be set as follows: INGRESS_DIRECTORY = watched-dir, where files in the subdirectory `passthrough-rest-data-source` are ingressed with `passthrough-rest-data-source` as the dataSource. In the example structure below the `hello.txt` file will be posted to DeltaFi with the `passthrough-rest-data-source` dataSource set.

```
watched-dir
└── passthrough-rest-data-source
    └── hello.txt
```

Environment variables:

| Name | Default | Desc                               |
|------|---------|------------------------------------|
|INGRESS_DIRECTORY|/data/file-ingress| root directory to watch            |
|CORE_URL|http://delta-core-service| base URL where data will be posted |


