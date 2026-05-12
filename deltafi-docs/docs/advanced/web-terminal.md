# Web Terminal (Beta)

::: warning Beta
The Web Terminal is currently in beta. Features and behavior may change in future releases.
:::

The Web Terminal provides a browser-based terminal for running DeltaFi CLI commands directly from the DeltaFi UI. It is a Compose-only feature and is not available in Kubernetes deployments.

::: warning Shared Session
The Web Terminal runs a single tmux session inside the container. All admin users who open the Web Terminal will connect to the same session and can see each other's input and output in real time. Treat it as a shared console, not a private shell.
:::

## Enabling the Web Terminal

The Web Terminal is disabled by default. To enable it, add the following to your `site/values.yaml`:

```yaml
deltafi:
  webTerminal:
    enabled: true
```

Then apply the configuration:

```bash
deltafi up
```

Once enabled, a **Web Terminal** menu item will appear in the Administration sidebar of the DeltaFi UI. You can also find it via the command palette (`Cmd+K` / `Ctrl+K`) by searching for "web terminal" or "shell".

To disable it, set `enabled` to `false` and run `deltafi up` again. Disabling the Web Terminal stops the container and removes the menu item from the UI. Any active terminal sessions will be terminated.

## Unavailable Commands

The Web Terminal runs inside a container that communicates with DeltaFi over its internal API. Commands that manage the host orchestration lifecycle or require direct access to service containers are not registered in the Web Terminal and will not appear in help or tab completion.

| Command | Reason |
|---------|--------|
| `deltafi up` | Manages the Compose stack lifecycle |
| `deltafi down` | Manages the Compose stack lifecycle |
| `deltafi upgrade` | Manages the Compose stack lifecycle |
| `deltafi config --wizard`, `--compose`, etc. | Modifies orchestration config (`deltafi config` and `--json` are available) |
| `deltafi valkey` | Requires exec access to the Valkey container |
| `deltafi postgres` | Requires exec access to the Postgres container |
| `deltafi lookup postgres` | Requires exec access to the lookup Postgres container |
| `deltafi docker-login` | Manages Docker registry credentials on the host |
| `deltafi kind` | KinD orchestration (not applicable in Compose) |
| `deltafi cli` | Deprecated host CLI passthrough |
| `deltafi compose` | Deprecated host Compose passthrough |

All other commands — including `deltafi status`, `deltafi flows`, `deltafi plugin`, `deltafi search`, `deltafi lookup`, `deltafi snapshot`, and others — work normally through the DeltaFi API.

## Using the Web Terminal

After clicking **Web Terminal** in the sidebar, a browser-based terminal opens with a tmux session named `DeltaFi`. The session is preconfigured with:

- The `deltafi` CLI on the PATH with bash completion
- Standard tools: `git`, `curl`, `jq`, `vim`, `tree`

### Common Commands

```bash
# Check system status
deltafi status

# List running flows
deltafi flows

# View system configuration
deltafi config

# Search for DeltaFiles
deltafi search --name "example.json"

# Install a plugin
deltafi plugin install <plugin-coordinates>
```

## tmux Basics

The terminal runs inside a [tmux](https://github.com/tmux/tmux/wiki) session. If you navigate away and return, you will reconnect to the same session with your command history preserved.

Both `Ctrl-a` and the default `Ctrl-b` work as the tmux prefix key.

### Window and Pane Management

| Shortcut | Action |
|----------|--------|
| `Ctrl-a c` | Create a new window |
| `Ctrl-a n` | Next window |
| `Ctrl-a p` | Previous window |
| `Ctrl-a \|` | Split pane vertically |
| `Ctrl-a _` | Split pane horizontally |
| `Ctrl-a ←/→/↑/↓` | Navigate between panes |
| `Ctrl-a d` | Detach (reconnect by returning to the page) |

### Copy and Paste

The tmux session intercepts mouse events, so standard browser text selection is not available.

**Within the terminal**, use tmux copy mode:

1. `Ctrl-a [` to enter copy mode
2. Click and drag to select text (or use arrow keys)
3. Press `Enter` to yank the selection into the tmux buffer
4. `Ctrl-a ]` to paste from the tmux buffer

**To and from the browser clipboard** (requires HTTPS or localhost):

When the DeltaFi UI is served over HTTPS (or accessed via `localhost`), yanking in tmux copy mode automatically pushes the selection to the browser clipboard via OSC 52. You can then paste into other applications with `Cmd+V` / `Ctrl+V`, and paste into the terminal the same way.

::: tip
Browser clipboard integration does not work over plain HTTP. This is a browser security restriction — the [Clipboard API requires a secure context](https://developer.mozilla.org/en-US/docs/Web/Security/Secure_Contexts). Over HTTP, use tmux copy mode to copy and paste within the terminal.
:::

## Security

The Web Terminal is proxied through the DeltaFi nginx gateway with the same authentication used by all other DeltaFi UI endpoints. Access requires:

- A valid authenticated session
- The `Admin` permission

There is no direct network access to the Web Terminal container. All traffic is routed through the authenticated proxy at `/shell/`.

## Related Documentation

- [TUI (Text User Interface)](/operating/TUI) — CLI reference for all `deltafi` commands
- [Site Configuration](/operating/site-configuration) — Configuring `site/values.yaml`
