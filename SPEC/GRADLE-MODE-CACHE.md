# Gradle Daemon Caches Stale Orchestration Mode

## Problem

`./gradlew install` produces the wrong task graph when `~/.deltafi/config.yaml`'s `orchestrationMode` changes between runs without restarting the Gradle daemon. Concretely:

1. Developer runs `./gradlew install` with `orchestrationMode: Compose`. The daemon spins up, evaluates the build, and caches the project model — which **does not include** any `pushToKind` tasks (they're created inside an `if (orchestrationMode == 'kind') { ... }` block).
2. Developer edits `~/.deltafi/config.yaml` to `orchestrationMode: KinD` (e.g. to test K8s deploy).
3. Developer runs `./gradlew install` again. The daemon reuses the cached project model from step 1. No `pushToKind` tasks exist in that model, so docker images get built and tagged but **never loaded into the kind cluster**.
4. `kubectl get pods` shows `ImagePullBackOff` everywhere because the cluster's containerd has no local images and tries to pull from docker.io, where snapshot tags don't exist.

The only workaround today is `./gradlew --stop` (kills daemons, forces re-evaluation) before the second install. This is non-obvious and easy to miss.

## Root Cause

In `build.gradle`:

```gradle
ext.deltafiConfig = loadDeltafiConfig();
...
if (!deltafiConfig.isEmpty()) {
    var orchestrationMode = deltafiConfig['orchestrationMode']?.toLowerCase()
    if (orchestrationMode == 'kind') {
        task startKindCluster (type: Exec) { ... }
        task syncKindPods (type: Exec) { ... }
    }
    if (orchestrationMode == 'compose') {
        task syncComposeServices (type: Exec) { ... }
    }
}
```

In `buildSrc/src/main/groovy/docker-conventions.gradle`:

```gradle
if (orchestrationMode == 'kind') {
    task pushToKind(type: Exec) {
        commandLine "deltafi", "kind", "load", "deltafi/${project.name}:${project.version}"
        dependsOn 'dockerTagDeltafi'
    }
}
```

Both are **configuration-time** decisions. The Gradle daemon caches the configured project model and reuses it across invocations as long as the build scripts themselves haven't changed. `~/.deltafi/config.yaml` is read at configuration time but is not declared as a build input, so the daemon has no way to know it changed and no reason to invalidate the cache.

This isn't unique to mode flips — any state read at configuration time from a file outside the build tree (env vars, external configs, etc.) has the same hazard. Mode-switching is just the most visible instance.

## Solutions

### A. Defer the mode check to execution time (recommended)

Always define all four conditional tasks (`pushToKind`, `startKindCluster`, `syncKindPods`, `syncComposeServices`), and guard each one with an `onlyIf {}` closure that re-reads `~/.deltafi/config.yaml` fresh at execution time. The task graph is mode-independent; execution skips tasks that don't apply right now.

```gradle
// build.gradle (root)
ext.currentOrchestrationMode = {
    def f = findDeltafiConfigFile()
    if (!f) return null
    return new Yaml().load(f.newInputStream())?.orchestrationMode?.toLowerCase()
}

task startKindCluster(type: Exec) {
    onlyIf { currentOrchestrationMode() == 'kind' }
    commandLine "deltafi", "kind", "up"
    dependsOn tui
}

task syncKindPods(type: Exec) {
    onlyIf { currentOrchestrationMode() == 'kind' }
    commandLine "deltafi", "up", "--force"
    dependsOn tui
}

task syncComposeServices(type: Exec) {
    onlyIf { currentOrchestrationMode() == 'compose' }
    // ...
}

installPrerequisites.dependsOn startKindCluster
```

```gradle
// buildSrc/src/main/groovy/docker-conventions.gradle
task pushToKind(type: Exec) {
    onlyIf { rootProject.currentOrchestrationMode() == 'kind' }
    commandLine "deltafi", "kind", "load", "deltafi/${project.name}:${project.version}"
    dependsOn 'dockerTagDeltafi'
}

task installDockerImages {
    dependsOn 'dockerTagDeltafi'
    dependsOn 'pushToKind'   // gated by onlyIf at exec time
    mustRunAfter rootProject.installPrerequisites
}

installDockerImages.finalizedBy rootProject.syncComposeServices  // onlyIf gates
installDockerImages.finalizedBy rootProject.syncKindPods         // onlyIf gates
install.dependsOn 'installDockerImages'
```

`onlyIf {}` closures evaluate at task execution time, so they always read the *current* config file no matter what the daemon has cached. Skipped tasks report as `SKIPPED` in the build output — clear signal of what's being applied for the current mode.

**Pros:** zero daemon restarts ever, mode flip is instant, clean diagnostic when reading the build output.
**Cons:** every task always shows up in `./gradlew tasks` regardless of mode (small ergonomic cost, mostly noise).

### B. Detect config change and force daemon restart

Hash `~/.deltafi/config.yaml` at the top of `settings.gradle`, compare against `.gradle/last-config-hash` stored in the project. If different, fail with a clear "run `./gradlew --stop` first" message.

**Pros:** ~20 lines of code, no refactor.
**Cons:** doesn't actually fix the bug — just surfaces it loudly. Developers still have to restart the daemon manually. Inferior to (A) on every axis except size of diff.

### C. Enable Gradle's configuration cache with provider APIs

```properties
# gradle.properties
org.gradle.configuration-cache=true
```

```gradle
def configContents = providers.fileContents(
    layout.projectDirectory.file("${System.getProperty('user.home')}/.deltafi/config.yaml")
).asText
```

`providers.fileContents` tells Gradle to track the file's content as a build input. When the file changes, the configuration cache automatically invalidates and rebuilds the project model. This is the idiomatic modern-Gradle path.

**Pros:** elegant, also accelerates subsequent builds (configuration cache reuses the configured task graph across runs when nothing changed).
**Cons:** enabling configuration cache is a non-trivial migration. Several existing tasks and plugins likely aren't cache-compatible without rework — anything that reads `project.foo` at runtime, anything in `doLast` closures referencing `project`, any third-party plugin not on the [supported list](https://docs.gradle.org/current/userguide/configuration_cache.html). Expect several hours of plugin-by-plugin debugging.

## Recommendation

**Option A.** Roughly 30 minutes of work, fixes the bug at the architectural level, and doesn't require enabling Gradle's configuration cache (which is its own project's worth of cleanup). The `onlyIf` execution-time evaluation is exactly the right escape hatch for "state that lives outside the build but affects task graph behavior."

Option C is what you'd reach for next, once we want the configuration-cache speedup independently of solving this particular bug.

## Touch points if implementing Option A

- `build.gradle` (root): define `ext.currentOrchestrationMode` helper; convert `startKindCluster` / `syncKindPods` / `syncComposeServices` blocks to always-defined + `onlyIf`-gated.
- `buildSrc/src/main/groovy/docker-conventions.gradle`: drop the `if (orchestrationMode == 'kind')` wrapping `pushToKind`; add `onlyIf` to the task; same for `installDockerImages.finalizedBy` and `dockerTagDeltafi.dependsOn` lines.
- `gradle-plugin/src/main/groovy/org.deltafi.plugin-convention.gradle`: review its `pushToKind` definition (line ~254) for the same pattern and apply the same treatment.
- Optional: add a smoke test that flips `orchestrationMode` between runs (without `--stop`) and verifies both modes' tasks fire correctly.
