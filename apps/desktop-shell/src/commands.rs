//! Native bridge: the few things a web page cannot do on its own. Every command is listed in `build.rs` and
//! granted per origin by the capabilities (`capabilities/default.json` for the splash, a runtime capability for the
//! configured web address), so the page can call nothing else. The pure helpers are tested without a window.

use base64::prelude::*;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use tauri::{AppHandle, Manager, State};
use tauri_plugin_notification::NotificationExt;
use tauri_plugin_opener::OpenerExt;

use crate::config::WebTarget;

/// Biggest file the bridge writes (the page hands over the whole file as base64).
pub const MAX_EXPORT_BYTES: usize = 50 * 1024 * 1024;

/// File types the page may save. Anything executable is out on purpose.
pub const EXPORT_EXTENSIONS: [&str; 5] = ["pdf", "csv", "xlsx", "txt", "json"];

const NOTIFICATION_TITLE_MAX: usize = 120;
const NOTIFICATION_BODY_MAX: usize = 500;

/// Shell metadata for the splash and support.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ShellInfo {
    pub version: String,
    pub os: String,
    pub arch: String,
}

// ---- export files -----------------------------------------------------------------------------------------

/// Folder the bridge saves to: `<Documents>/Itera/exports`.
pub fn export_dir(documents: &Path) -> PathBuf {
    documents.join("Itera").join("exports")
}

const WINDOWS_RESERVED: [&str; 22] = [
    "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1",
    "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
];

/// A plain file name (no folders, no tricks) with an allowed extension; returns the trimmed name.
pub fn validate_export_name(name: &str) -> Result<String, String> {
    let name = name.trim();
    if name.is_empty() || name.chars().count() > 128 {
        return Err("The file name must be 1 to 128 characters".to_string());
    }
    if name.starts_with('.') || name.ends_with('.') {
        return Err("The file name must not start or end with a dot".to_string());
    }
    if name.chars().any(|c| c.is_control() || matches!(c, '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|')) {
        return Err("The file name contains characters that are not allowed".to_string());
    }
    let path = Path::new(name);
    let extension = path.extension().and_then(|e| e.to_str()).map(str::to_ascii_lowercase).unwrap_or_default();
    if !EXPORT_EXTENSIONS.contains(&extension.as_str()) {
        return Err(format!("Only {} files can be saved", EXPORT_EXTENSIONS.join(", ")));
    }
    let stem = path.file_stem().and_then(|s| s.to_str()).unwrap_or_default().to_ascii_uppercase();
    if WINDOWS_RESERVED.contains(&stem.as_str()) {
        return Err("That file name is reserved by the operating system".to_string());
    }
    Ok(name.to_string())
}

/// `name`, or `name (1)`, `name (2)` ... when a file of that name exists already: an export never overwrites.
fn unique_path(dir: &Path, name: &str) -> PathBuf {
    let first = dir.join(name);
    if !first.exists() {
        return first;
    }
    let path = Path::new(name);
    let stem = path.file_stem().and_then(|s| s.to_str()).unwrap_or(name);
    let extension = path.extension().and_then(|e| e.to_str()).unwrap_or("");
    for n in 1..10_000 {
        let candidate = dir.join(format!("{stem} ({n}).{extension}"));
        if !candidate.exists() {
            return candidate;
        }
    }
    first
}

/// Decodes `base64_data` and writes it into `dir` (created if needed). Returns the path written.
pub fn save_export_to(dir: &Path, file_name: &str, base64_data: &str) -> Result<PathBuf, String> {
    save_export_limited(dir, file_name, base64_data, MAX_EXPORT_BYTES)
}

/// [`save_export_to`] with an explicit size limit (the tests use a small one).
pub fn save_export_limited(dir: &Path, file_name: &str, base64_data: &str, max_bytes: usize) -> Result<PathBuf, String> {
    let name = validate_export_name(file_name)?;
    let clean = base64_data.trim();
    if clean.is_empty() {
        return Err("There is no data to save".to_string());
    }
    // 4 base64 characters carry 3 bytes: refuse before decoding something huge
    if clean.len() / 4 * 3 > max_bytes {
        return Err("The file is too large to save".to_string());
    }
    let bytes = BASE64_STANDARD.decode(clean).map_err(|e| format!("The data is not valid base64: {e}"))?;
    if bytes.len() > max_bytes {
        return Err("The file is too large to save".to_string());
    }
    fs::create_dir_all(dir).map_err(|e| format!("Could not create {}: {e}", dir.display()))?;
    let path = unique_path(dir, &name);
    fs::write(&path, &bytes).map_err(|e| format!("Could not write {}: {e}", path.display()))?;
    Ok(path)
}

/// The existing export `file_name` inside `dir`, or an error. Nothing outside `dir` can be reached.
pub fn existing_export(dir: &Path, file_name: &str) -> Result<PathBuf, String> {
    let name = validate_export_name(file_name)?;
    let path = dir.join(&name);
    let canonical = path.canonicalize().map_err(|_| format!("{name} was not found in the export folder"))?;
    let root = dir.canonicalize().map_err(|_| "The export folder does not exist yet".to_string())?;
    if !canonical.starts_with(&root) || !canonical.is_file() {
        return Err(format!("{name} was not found in the export folder"));
    }
    Ok(canonical)
}

fn documents_export_dir(app: &AppHandle) -> Result<PathBuf, String> {
    let documents = app.path().document_dir().map_err(|e| format!("Documents folder not available: {e}"))?;
    Ok(export_dir(&documents))
}

/// Saves a file the page produced (CSV, PDF ...) into `<Documents>/Itera/exports` and returns its path.
#[tauri::command]
pub fn save_export_file(app: AppHandle, file_name: String, base64_data: String) -> Result<String, String> {
    let path = save_export_to(&documents_export_dir(&app)?, &file_name, &base64_data)?;
    Ok(path.to_string_lossy().to_string())
}

/// Opens a file of the export folder with the default application.
#[tauri::command]
pub fn open_export_file(app: AppHandle, file_name: String) -> Result<(), String> {
    let path = existing_export(&documents_export_dir(&app)?, &file_name)?;
    app.opener().open_path(path.to_string_lossy(), None::<&str>).map_err(|e| format!("Could not open the file: {e}"))
}

// ---- notifications ----------------------------------------------------------------------------------------

/// Title and body of a notification, trimmed and length-limited; both must have text.
pub fn clean_notification(title: &str, body: &str) -> Result<(String, String), String> {
    let title = title.trim();
    let body = body.trim();
    if title.is_empty() || body.is_empty() {
        return Err("A notification needs a title and a text".to_string());
    }
    let cut = |s: &str, max: usize| s.chars().take(max).collect::<String>();
    Ok((cut(title, NOTIFICATION_TITLE_MAX), cut(body, NOTIFICATION_BODY_MAX)))
}

/// Shows an operating system notification (the text is passed to the notification API as data, never to a shell).
#[tauri::command]
pub fn send_notification(app: AppHandle, title: String, body: String) -> Result<(), String> {
    let (title, body) = clean_notification(&title, &body)?;
    app.notification().builder().title(title).body(body).show().map_err(|e| format!("Could not show the notification: {e}"))
}

// ---- shell ------------------------------------------------------------------------------------------------

/// Version, operating system and architecture of the shell.
#[tauri::command]
pub fn get_shell_info() -> ShellInfo {
    ShellInfo {
        version: env!("CARGO_PKG_VERSION").to_string(),
        os: std::env::consts::OS.to_string(),
        arch: std::env::consts::ARCH.to_string(),
    }
}

/// The web address to load (and a warning when `ITERA_WEB_URL` had to be ignored). Used by the splash.
#[tauri::command]
pub fn get_target_web_url(target: State<'_, WebTarget>) -> WebTarget {
    target.inner().clone()
}

/// The web page draws its own title bar: hide the native frame while it is there, bring it back when it is not.
/// macOS keeps its traffic lights (overlay title bar), so nothing changes there.
#[tauri::command]
pub fn use_custom_chrome(window: tauri::WebviewWindow, enabled: bool) -> Result<(), String> {
    #[cfg(not(target_os = "macos"))]
    window.set_decorations(!enabled).map_err(|e| format!("Could not change the window frame: {e}"))?;
    #[cfg(target_os = "macos")]
    let _ = (window, enabled);
    Ok(())
}
