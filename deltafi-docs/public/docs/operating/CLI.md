# CLI

```
Usage: $cli_name [command]

Commands:
  did                    show did for filename
  egress-flow            start, stop or validate an egress flow
  enrich-flow            start, stop or validate an enrich flow
  export-config          export all the loaded flow configurations as yaml
  export-egress-plan     export a single egress flow plan by name as JSON
  export-enrich-plan     export a single enrich flow plan by name as JSON
  export-ingress-plan    export a single ingress flow plan by name as JSON
  export-rules           export all ingress-flow assignment rules as JSON
  ingress                ingress one or more files to a flow
  ingress-flow           start, stop or validate an ingress flow
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
  mongo-cli              launch the mongo CLI
  mongo-eval             eval a command in mongo
  mongo-migrate          run mongo migrations
  performance-test       run performance tests against DeltaFi
  plugin-customization   manage plugin customization configuration
  plugin-image-repo      manage the image repositories that hold plugin images
  query                  send a query to graphql
  redis-cli              launch the redis CLI
  redis-latency          monitor redis latency
  redis-stats            monitor redis connection stats
  redis-watch            watch every command issued to redis
  secrets                show k8s secrets
  serviceip              show service IP
  set-admin-password     set the admin password
  start                  (alias for install)
  status                 show status of system
  stop                   (alias for uninstall)
  system-snapshot        manage system state snapshots
  system-property        manage system properties
  trace                  show trace data for a DID
  update                 (alias for install)
  upgrade                (alias for install)
  uninstall              uninstall the DeltaFi core
  uninstall-plugin       uninstall a DeltaFi plugin
  versions               show running versions
  *                      show help

Flags:
  --help            show help for a specific command
  -v                enable verbose output
```