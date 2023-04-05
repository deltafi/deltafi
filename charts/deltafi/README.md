# DeltaFi chart instructions

## Subcharts

The subcharts for DeltaFi are located in the `charts` directory.  Each chart is synced from a separate repo.  The `dependency-sync` script is used to sync charts from the subchart repo.

### Updating a subchart

1. Make chart changes in the subchart's git repo
1. Tag the subchart with a unique tag
1. Update the tag in the `dependency-sync` script
1. Run the `dependency-sync` script
1. Commit the updated chart changes and the `dependency-sync` script change

### Pro tip - update a subchart fork repo from a canonical chart

1. Uncomment the repository for the chart in `Chart.yaml`
1. Update the version of the chart
1. Run `helm dependency update`.  This will place a tar.gz file in the `charts` dir
1. `cd charts`
1. `rm -rf <subchart>` (leaving the subchart tar.gz)
1. `git clone <subchart fork URI> <subchart>`
1. `rm -rf <subchart>/*`
1. `tar xvf <subchart>.tar.gz>`
1. `cd <subchart>`
1. `git commit -m "<something pithy>" -a && git tag -a -m <subchart version> <subchart version> && git push && git push --tags`
1. `cd ../..`
1. `rm -rf charts/<subchart>*` to clean out the subchart stuff
1. Update the subchart tag in `dependency-sync`
1. `./dependency-sync`
1. Add and commit the subchart changes
