# Itera Enterprise Workstation (Tauri v2 desktop shell)

A thin desktop shell around the Itera admin web application. It bundles no UI: it opens the web application in the
system WebView (WebView2 on Windows, WKWebView on macOS, WebKitGTK on Linux) and adds the few native things a web
page cannot do. The web application stays deployed on the server, so a new web version needs no client update.

Targets: Windows and macOS. Linux is configured but not yet verified.

## Configuration

The shell needs to know one thing: the web address.

| Order | Source |
|---|---|
| 1 | environment variable `ITERA_WEB_URL` (read at start-up, no rebuild) |
| 2 | address baked in at build time with `ITERA_DEFAULT_WEB_URL` (set it when building the installer) |
| 3 | `http://localhost:3000` |

Only plain `http(s)` addresses without credentials are accepted. An invalid `ITERA_WEB_URL` is ignored and the splash
shows why, so a typo never leaves a blank window.

```powershell
$env:ITERA_WEB_URL = "https://admin.example.com"; .\itera-workstation.exe
```

## What the window does

- **Splash** (`ui/index.html`, the only page inside the exe): probes the address, retries every 2 s, shows the cause and
  a manual retry after a failure, then opens the web application.
- **Title bar**: drawn by the web application (`apps/admin/src/features/shell/ShellTitleBar.tsx`), not by the shell.
  The shell tells the page it is running in the shell (`window.__ITERA_SHELL__`); the page asks the shell to hide the
  native frame (`use_custom_chrome`) and draws the bar with drag area and minimize / maximize / close. If the shell
  refuses, or a page has no bar (an error page, an older web version), the native frame stays. On macOS the traffic
  lights stay and the page only leaves room for them.
- **Window**: remembers size, position and maximized state; minimum 1024x640; a second launch focuses the running window.
- **Navigation**: only the configured address and the shell's own pages load in the window. Other links (and
  `target="_blank"`) open in the default browser.

## Native bridge (what a page may call)

Every command is listed in `build.rs`; a command that is not listed cannot be granted. The splash gets
`capabilities/default.json`; the configured web address gets a capability added at start-up (`src/window.rs`), because
it is only known at run time. Nothing else is allowed, and any other origin cannot call anything.

| Command | Purpose |
|---|---|
| `get_target_web_url`, `get_shell_info` | splash only |
| `use_custom_chrome(enabled)` | hide / show the native frame (Windows, Linux) |
| `save_export_file(file_name, base64_data)` | save a file the page produced into `<Documents>/Itera/exports` |
| `open_export_file(file_name)` | open a file of that folder with the default application |
| `send_notification(title, body)` | operating system notification |

Files are limited to a plain name with the extension pdf, csv, xlsx, txt or json, at most 50 MiB, and never overwrite an
existing file (`name (1).ext`). A path, a drive, `..` or a reserved device name is refused. Notification text goes to the
notification API as data, never to a shell.

## Build

```bash
# development exe (loads the splash from ui/, then the web address)
cargo build --features custom-protocol

# release exe: target/release/desktop-shell.exe
cargo build --release --features custom-protocol
```

`custom-protocol` embeds `ui/` into the exe. Without it the shell would look for a dev server at `devUrl`.

### Installer

`tauri.conf.json` holds the product name (**Itera Enterprise Workstation**), identifier (`com.itera.workstation`) and
icons; `tauri.windows.conf.json`, `tauri.macos.conf.json` and `tauri.linux.conf.json` choose the bundle per platform
(Windows: NSIS installer for the current user, no administrator rights; macOS: `.app` and `.dmg`; Linux: `.deb` and
AppImage). Installers are built with the Tauri CLI on the platform they are for:

```bash
cargo install tauri-cli --locked          # once
ITERA_DEFAULT_WEB_URL=https://admin.example.com cargo tauri build
```

Result on Windows: `target/release/bundle/nsis/Itera Enterprise Workstation_1.0.0_x64-setup.exe` (about 2.5 MB; the app
itself is `itera-workstation.exe`, about 11 MB). The first build downloads NSIS and its Tauri helper DLL (hash
verified). A running copy of the shell locks `target/release/desktop-shell.exe`; set `CARGO_TARGET_DIR=target-dist` to
build next to it (a full rebuild, about 5 minutes).

The Windows installer downloads the WebView2 bootstrapper when the runtime is missing. **The installers are not code
signed**: Windows SmartScreen and macOS Gatekeeper will warn on first launch (macOS: right-click, Open). Signing is a
later step and needs a certificate.

### Icons

The files in `icons/` are placeholders (a teal square with an "I"), generated for the shell. Replace them with the real
icon: `cargo tauri icon path/to/icon-1024.png` regenerates the whole set under the same names.

## Verification

```bash
cargo check
cargo test        # configuration, export-file and notification rules (no window needed)
```

The web side of the title bar is covered by `e2e/tests/shell.spec.ts` (the shell is stubbed in the browser). The
native window itself is checked by running the exe against the e2e stack:

```powershell
# memory stack: backend on 18080, admin on 13000 (see e2e/README.md), then
$env:ITERA_WEB_URL = "http://localhost:13000"; .\target\debug\desktop-shell.exe
```
