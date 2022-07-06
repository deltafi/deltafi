### One command to run them all:

Prerequisites:

- Check out the deltafi repo and plow into the `kind` dir.
- KinD
  - MacOS: brew install kind
- 40-50Gb of clear disk space
- At least 4 cores allocated to docker
- The following tools installed:
  - kubectl
  - kubens
  - docker
  - tree
  - helm
- recommend running `brew bundle` from the root to install needed tools on MacOS

One time setup script:

```
./install.sh # Just the first time, yo
```

Get the latest released cluster up and running:

```
cluster up
cluster install
```

Then point your browser at `https://local.deltafi.org`

### What it does:

- Launches a KinD (Kubernetes in Docker) cluster and runs deltafi
- Spins up 3 docker proxies and a docker sideload registry
- Enables shell access to the cluster where the deltafi command can run
- Provides a quick workflow for testing local DeltaFi changes in a real deployment configuration

### What is awesome:

- All pods launch and get into a healthy state in about 60 seconds (when the caches are primed)
- Local web ingress
	- http://local.deltafi.org for the UI
	  - `http://local.deltafi.org/api/v1/*` for the API
	  - http://local.deltafi.org/api/v1/config
	- http://grafana.local.deltafi.org (Grafana front end)
	- http://k8s.local.deltafi.org (k8s dashboard)
- Caches are in place, so once they are primed, docker images are pulled locally
- Persistent volumes are mounted in the kind/data directory
- Metrics server is installed and working
- End-to-end passthrough dataflow and smoke work
- Plugins work

## Commands

- `cluster up` - Start a KinD cluster on your local box.  You can poke at this cluster whichever way with `kubectl` and whatnot.  It is just a cluster running in a docker container.
- `cluster down` - Stop the kind cluster (and anything running in it).
- `cluster destroy` - Stop the kind cluster and nuke persistent volumes, for that clean, fresh feeling.
- `cluster install` - Start up a release DeltaFi (whatever is pointed to in the local working copy, like `deltafi install`)
- `cluster uninstall` - Stop a running DeltaFi (like `deltafi uninstall`) and leave cluster otherwise intact.
- `cluster run <blah>` - Run a command inside the kind control node. ex. `cluster run deltafi versions`
- `cluster shell` - Launch a shell tmux session inside the kind control node.  You can do all the Linux here.
- `cluster loc <subcommands>` - Local build operations.  This command is used to control the building and installing of local builds of DeltaFi.  This is optimized for quick turnaround development cycles.  The various subcommands:
  - `build` - Build a complete set of DeltaFi docker images locally.  You must have deltafi and the plugin repos checked out at the same directory level.
  - `clean` - Modifies build to be a clean build instead of an incremental one.
  - `install` - Install DeltaFi and all the plugins that are built locally.  Whatever is currently running and up to date will just keep on running.
  - `reinstall` - Uninstall and install DeltaFi and all the plugins.
  - `restart` - Down the whole cluster and then do an install
  - `bounce <something>` - Restart a particular pod or set of pods as a substring match.  `cluster loc bounce core` will bounce deltafi-core-.*.  `cluster loc bounce deltafi` will restart all the things.
  - `-f or force` - Throw caution to the wind and skip "Are you sure?"  Just ask yourself, "Are you sure?"
  - Any of the subcommands can be strung together to create a tasty workflow stew:
    - `cluster loc clean build restart -f` - Build all the things, start up a new cluster running it
    - `cluster loc build bounce deltafi-api` - For those quick turnaround builds of deltafi-api
    - `cluster loc make me a sammich` - You will get helpful usage tips
