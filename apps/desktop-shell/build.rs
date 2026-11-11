fn main() {
    // List every app command: a command that is not listed here cannot be granted to any page.
    tauri_build::try_build(tauri_build::Attributes::new().app_manifest(
        tauri_build::AppManifest::new().commands(&[
            "get_shell_info",
            "get_target_web_url",
            "use_custom_chrome",
            "save_export_file",
            "open_export_file",
            "send_notification",
        ]),
    ))
    .expect("failed to run the tauri build script");
}
