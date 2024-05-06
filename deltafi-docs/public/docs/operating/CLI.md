# CLI

```
Usage: deltafi [command]

Commands:
  clickhouse-cli         launch the clickhouse CLI
  did                    show did for filename
  disable                stop all DeltaFi processes
  egress-flow            start, stop or validate an egress flow
  enrich-flow            start, stop or validate an enrich flow
  export-config          export all the loaded flow configurations as yaml
  export-egress-plan     export a single egress flow plan by name as JSON
  export-enrich-plan     export a single enrich flow plan by name as JSON
  export-normalize-plan  export a single normalize flow plan by name as JSON
  export-transform-plan  export a single transform flow plan by name as JSON
  export-rules           export all ingress-flow assignment rules as JSON
  ingress                ingress one or more files to a flow
  install                install/upgrade the DeltaFi core
  install-plugin         install/upgrade a DeltaFi plugin
  list-actions           list the actions registered with deltafi
  list-flows             list the flows in the system with their status
  list-plans             list all flow plans within deltafi
  list-plugins           list all installed plugins
  list-policies          list delete policies
  load-plans             load the flow plans and variables for the given plugin
  load-policies          load delete policies from a JSON file
  load-rules             load ingress flow assignment rules from a JSON file
  minio-cli              launch the minio CLI
  mongo-cli              launch the mongo CLI
  mongo-eval             eval a command in mongo
  mongo-migrate          run mongo migrations
  normalize-flow         start, stop or validate an normalize flow
  performance-test       run performance tests against DeltaFi
  postgres-cli           launch the Postgres CLI
  plugin-customization   manage plugin customization configuration
  plugin-image-repo      manage the image repositories that hold plugin images
  plugin-init            create a new plugin project
  query                  send a query to graphql
  reenanble              reenable all DeltaFi processes
  registry               manage local Docker registry
  scale                  edit the replica counts for all deployments and statefulsets
  secrets                show k8s secrets
  serviceip              show service IP
  set-admin-password     set the admin password
  start                  (alias for install)
  status                 show status of system
  system-snapshot        manage system state snapshots
  system-property        manage system properties
  timed-ingress          start, stop or validate a timed ingress
  trace                  show trace data for a DID
  transform-flow         start, stop or validate a transform flow
  update                 (alias for install)
  upgrade                (alias for install)
  uninstall              uninstall the DeltaFi core
  uninstall-plugin       uninstall a DeltaFi plugin
  valkey-cli              launch the valkey CLI
  valkey-latency          monitor valkey latency
  valkey-stats            monitor valkey connection stats
  valkey-watch            watch every command issued to valkey
  version                print the current core version of DeltaFi
  versions               show running versions
  *                      show help

Flags:
  --help            show help for a specific command
  -v                enable verbose output
```
