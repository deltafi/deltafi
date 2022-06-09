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

```
bask cluster up
bask install
bask passthrough_some_data
```

### What it does:

- Launches a KinD cluster and runs deltafi
- Spins up 3 docker proxies and a docker sideload registry
- Enables shell access to the cluster where the deltafi command can run

### What works:

- All pods launch and get into a healthy state in about 10 minutes (when the caches are primed)
- Caches are in place, so once they are primed, docker images are pulled locally
- Persistent volumes are mounted in the ./mounts directory
- Metrics server is installed and working
- `deltafi install` and `deltafi uninstall` work
- Local web ingress
	- http://kind.deltafi.org for the UI
	  - `http://kind.deltafi.org/api/v1/*` for the API
	  - http://kind.deltafi.org/api/v1/config
	- http://minio.kind.deltafi.org (Minio front end)
	- http://k8s.kind.deltafi.org (k8s dashboard)
- When flows can be configured (see below for workaround) end-to-end passthrough dataflow works

### Jankyness:

- CLI config load doesn't work from host
- CLI ingest doesn't work from host

### Crazy workaround:

- CLI works fully inside the kind container, with a little initialization magic.
  So...just run `bask shell` and you will get a shell to the KinD container
  where you can run the deltafi command.  Note that your repositories are mounted
  in the /usr/dev path, so you can get at your source from inside the KinD
  container.
