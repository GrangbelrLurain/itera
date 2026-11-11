pub mod commands;
pub mod config;
pub mod window;

use tauri::Manager;
use tauri_plugin_window_state::StateFlags;

/// Run the Tauri v2 desktop shell application
pub fn run() {
    let builder = tauri::Builder::default();

    // a second launch brings the running window to the front instead of opening another one
    #[cfg(desktop)]
    let builder = builder.plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
        if let Some(window) = app.get_webview_window(window::MAIN) {
            let _ = window.unminimize();
            let _ = window.show();
            let _ = window.set_focus();
        }
    }));

    builder
        // remember size, position and maximized state (not the window frame: the page decides that)
        .plugin(
            tauri_plugin_window_state::Builder::default()
                .with_state_flags(StateFlags::SIZE | StateFlags::POSITION | StateFlags::MAXIMIZED)
                .build(),
        )
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_notification::init())
        .on_page_load(|webview, payload| window::on_page_load(webview, &payload.event()))
        .invoke_handler(tauri::generate_handler![
            commands::get_shell_info,
            commands::get_target_web_url,
            commands::use_custom_chrome,
            commands::save_export_file,
            commands::open_export_file,
            commands::send_notification,
        ])
        .setup(|app| {
            let target = config::web_target_from_env();
            if let Some(warning) = &target.warning {
                eprintln!("[itera shell] {warning}");
            }
            app.manage(target.clone());
            window::build_main_window(app.handle(), &target)?;
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running the Itera desktop shell");
}
