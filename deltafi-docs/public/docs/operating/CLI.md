# CLI

```
Usage: deltafi [command]

Commands:
  configure-plugin-ssl    configure SSL for plugins and the entity resolver
  data-sink               start, stop, show or validate a data sink
  data-source             start, stop, show or validate a data source flow
  did                     show did for filename
  disable                 stop all DeltaFi processes
  export-config           export all the loaded flow configurations as yaml
  export-data-source-plan export a single data source plan by name as JSON
  export-data-sink-plan   export a single data sink plan by name as JSON
  export-transform-plan   export a single transform plan by name as JSON
  ingress                 ingress one or more files
  install                 install/upgrade the DeltaFi core
  install-plugin          install/upgrade a DeltaFi plugin
  integration-test        run an integration test
  list-actions            list the actions registered with deltafi
  list-flows              list the flows in the system with their status
  list-plans              list all flow plans within deltafi
  list-plugins            list all installed plugins
  list-policies           list delete policies
  load-plans              load the flow plans and variables for the given plugin
  load-policies           load delete policies from a JSON file
  minio-cli               launch the minio CLI
  postgres-cli            launch the postgres CLI
  performance-test        run performance tests against DeltaFi
  plugin-image-repo       manage the image repositories that hold plugin images
  plugin-init             create a new plugin project
  postgres-cli            launch the Postgres CLI
  postgres-eval           run SQL in Postgres
  query                   send a query to graphql
  reenable                reenable all DeltaFi processes
  registry                manage local Docker registry
  scale                   edit the replica counts for all deployments and statefulsets
  secrets                 show k8s secrets
  serviceip               show service IP
  set-admin-password      set the admin password
  start                   (alias for install)
  status                  show status of system
  system-snapshot         manage system state snapshots
  system-property         manage system properties
  timed-data-source       start, stop, show and other controls for a timed data source
  trace                   show trace data for a DID
  transform               start, stop or validate a transform
  update                  (alias for install)
  upgrade                 (alias for install)
  uninstall               uninstall the DeltaFi core
  uninstall-plugin        uninstall a DeltaFi plugin
  valkey-cli              launch the valkey CLI
  valkey-latency          monitor valkey latency
  valkey-stats            monitor valkey connection stats
  valkey-watch            watch every command issued to valkey
  version                 print the current core version of DeltaFi
  versions                show running versions
  *                       show help

Flags:
  --help            show help for a specific command
  -v                enable verbose output
```
