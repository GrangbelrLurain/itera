//! The main window: created in code (not in tauri.conf.json) so that the navigation policy, the page-script and the
//! per-platform title bar can be set up before the first page loads.

use tauri::ipc::CapabilityBuilder;
use tauri::webview::{NewWindowResponse, PageLoadEvent};
use tauri::{AppHandle, Manager, Url, WebviewUrl, WebviewWindow, WebviewWindowBuilder};
use tauri_plugin_opener::OpenerExt;

use crate::config::{is_app_url, origin_pattern, parse_web_url, WebTarget};

pub const MAIN: &str = "main";
pub const TITLE: &str = "Itera Enterprise Workstation";

/// Permissions the configured web address gets: its own title bar needs the window controls, plus the bridge.
const REMOTE_PERMISSIONS: [&str; 9] = [
    "core:window:allow-minimize",
    "core:window:allow-toggle-maximize",
    "core:window:allow-is-maximized",
    "core:window:allow-close",
    "core:window:allow-start-dragging",
    "allow-use-custom-chrome",
    "allow-save-export-file",
    "allow-open-export-file",
    "allow-send-notification",
];

pub fn platform_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "macos"
    } else {
        "linux"
    }
}

/// Runs before every page: tells the web app that it lives in the shell, so it can draw the title bar.
fn init_script() -> String {
    format!(
        "Object.defineProperty(window, '__ITERA_SHELL__', {{ value: Object.freeze({{ platform: '{}', version: '{}' }}) }});",
        platform_name(),
        env!("CARGO_PKG_VERSION")
    )
}

/// Only the configured web address and the shell's own pages may load in the window; any other address is handed to
/// the default browser (links to documentation, mail addresses ...).
fn allow_navigation(url: &Url, web: &Url) -> bool {
    is_app_url(url) || url.origin() == web.origin() || url.scheme() == "about"
}

fn open_externally(app: &AppHandle, url: &Url) {
    if matches!(url.scheme(), "http" | "https" | "mailto" | "tel") {
        let _ = app.opener().open_url(url.as_str(), None::<&str>);
    }
}

/// Grants the configured web address the window controls and the bridge commands. Capabilities are static files
/// for everything the shell serves itself; the web address is only known at run time (`ITERA_WEB_URL`).
pub fn grant_web_origin(app: &AppHandle, web: &Url) -> tauri::Result<()> {
    let mut capability = CapabilityBuilder::new("web-remote").remote(origin_pattern(web)).local(false).window(MAIN);
    for permission in REMOTE_PERMISSIONS {
        capability = capability.permission(permission);
    }
    app.add_capability(capability)
}

/// Creates the main window showing the splash, which probes the web address and then navigates to it.
pub fn build_main_window(app: &AppHandle, target: &WebTarget) -> tauri::Result<WebviewWindow> {
    let web = parse_web_url(&target.url).expect("the target was validated by the configuration");
    grant_web_origin(app, &web)?;

    let nav_app = app.clone();
    let nav_web = web.clone();
    let window_app = app.clone();

    let builder = WebviewWindowBuilder::new(app, MAIN, WebviewUrl::App("index.html".into()))
        .title(TITLE)
        .inner_size(1366.0, 860.0)
        .min_inner_size(1024.0, 640.0)
        .resizable(true)
        .center()
        .initialization_script(init_script())
        .on_navigation(move |url| {
            if allow_navigation(url, &nav_web) {
                true
            } else {
                open_externally(&nav_app, url);
                false
            }
        })
        .on_new_window(move |url, _features| {
            // target="_blank" and window.open: never a second shell window
            open_externally(&window_app, &url);
            NewWindowResponse::Deny
        });

    // macOS keeps its traffic lights on top of the page; the page leaves room for them
    #[cfg(target_os = "macos")]
    let builder = builder.title_bar_style(tauri::TitleBarStyle::Overlay).hidden_title(true);

    builder.build()
}

/// A freshly loaded page starts with the native frame; a page that draws its own title bar switches it off again.
/// This keeps the window usable if a page (an error page, an older web version) has no title bar of its own.
pub fn on_page_load(webview: &tauri::Webview, event: &PageLoadEvent) {
    #[cfg(not(target_os = "macos"))]
    if matches!(event, PageLoadEvent::Started) {
        if let Some(window) = webview.app_handle().get_webview_window(MAIN) {
            let _ = window.set_decorations(true);
        }
    }
    #[cfg(target_os = "macos")]
    let _ = (webview, event);
}
