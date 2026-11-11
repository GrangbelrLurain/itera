use desktop_shell::commands::{
    clean_notification, existing_export, export_dir, get_shell_info, save_export_limited, save_export_to,
    validate_export_name, MAX_EXPORT_BYTES,
};
use std::fs;
use std::path::PathBuf;

const SAMPLE_PDF_BASE64: &str = "JVBERi0xLjQKMSAwIG9iago8PCAvVHlwZSAvQ2F0YWxvZyAvUGFnZXMgMiAwIFIgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL1R5cGUgL1BhZ2VzIC9LaWRzIFszIDAgUl0gL0NvdW50IDEgPj4KZW5kb2JqCjMgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCA2MTIgNzkyXSAvQ29udGVudHMgNCAwIFIgL1Jlc291cmNlcyA8PCAvRm9udCA8PCAvRjEgNSAwIFIgPj4gPj4gPj4KZW5kb2JqCjQgMCBvYmoKPDwgL0xlbmd0aCAxMDAgPj4Kc3RyZWFtCkJUIC9GMSAxMiBUZiAxMDAgNzAwIFRkIChBZXJvQ29yZSBUZXN0IEUtdGlja2V0KSBUaiBFVAplbmRzdHJlYW0KZW5kb2JqCjUgMCBvYmoKPDwgL1R5cGUgL0ZvbnQgL1N1YnR5cGUgL1R5cGUxIC9CYXNlRm9udCAvSGVsdmV0aWNhID4+CmVuZG9iagp4cmVmCjAgNgowMDAwMDAwMDAwIDY1NTM1IGYgCjAwMDAwMDAwMDkgMDAwMDAgbiAKMDAwMDAwMDA1OCAwMDAwMCBuIAowMDAwMDAwMTE1IDAwMDAwIG4gCjAwMDAwMDAyMjQgMDAwMDAgbiAKMDAwMDAwMDM3NSAwMDAwMCBuIAp0cmFpbGVyCjw8IC9TaXplIDYgL1Jvb3QgMSAwIFIgPj4Kc3RhcnR4cmVmCjQ1MAolJUVPRgo=";

/// A fresh folder under the system temp dir; removed at the end of the test by `Scratch::drop`.
struct Scratch(PathBuf);

impl Scratch {
    fn new(tag: &str) -> Self {
        let dir = std::env::temp_dir().join(format!("itera_shell_{tag}_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        Scratch(dir)
    }
}

impl Drop for Scratch {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.0);
    }
}

#[test]
fn export_folder_is_inside_documents() {
    let dir = export_dir(&PathBuf::from("docs"));
    assert!(dir.ends_with("Itera/exports") || dir.ends_with("Itera\\exports"), "{dir:?}");
}

#[test]
fn plain_names_with_allowed_extensions_pass() {
    for name in ["report.csv", " 예약 목록.xlsx ", "ticket-1.PDF", "a.b.json", "notes.txt"] {
        assert!(validate_export_name(name).is_ok(), "{name}");
    }
    assert_eq!(validate_export_name("  x.csv ").unwrap(), "x.csv");
}

#[test]
fn names_that_could_leave_the_folder_or_run_code_are_refused() {
    for name in [
        "", "   ", "../evil.csv", "..\\evil.csv", "dir/file.csv", "C:evil.csv", "file.csv:stream", ".hidden.csv",
        "trailing.csv.", "run.exe", "run.bat", "run.ps1", "noextension", "CON.txt", "nul.csv", "lpt1.pdf", "a*b.csv",
        "a\nb.csv", "a|b.csv",
    ] {
        assert!(validate_export_name(name).is_err(), "{name:?} must be refused");
    }
    assert!(validate_export_name(&format!("{}.csv", "a".repeat(130))).is_err());
}

#[test]
fn save_writes_the_decoded_bytes_and_creates_the_folder() {
    let scratch = Scratch::new("save");
    let dir = export_dir(&scratch.0);
    let path = save_export_to(&dir, "TK-1.pdf", SAMPLE_PDF_BASE64).expect("saved");
    assert_eq!(path, dir.join("TK-1.pdf"));
    let bytes = fs::read(&path).unwrap();
    assert!(bytes.starts_with(b"%PDF-1.4"));
    assert!(bytes.windows(5).any(|w| w == b"%%EOF"));
}

#[test]
fn save_never_overwrites() {
    let scratch = Scratch::new("unique");
    let dir = export_dir(&scratch.0);
    let first = save_export_to(&dir, "report.csv", "YSxi").unwrap(); // "a,b"
    let second = save_export_to(&dir, "report.csv", "YSxi").unwrap();
    let third = save_export_to(&dir, "report.csv", "YSxi").unwrap();
    assert_eq!(first.file_name().unwrap(), "report.csv");
    assert_eq!(second.file_name().unwrap(), "report (1).csv");
    assert_eq!(third.file_name().unwrap(), "report (2).csv");
    assert_eq!(fs::read(&first).unwrap(), b"a,b");
}

#[test]
fn save_rejects_bad_input_and_writes_nothing() {
    let scratch = Scratch::new("bad");
    let dir = export_dir(&scratch.0);
    assert!(save_export_to(&dir, "x.csv", "not-valid-base64!@@@$$%").unwrap_err().contains("base64"));
    assert!(save_export_to(&dir, "x.csv", "   ").is_err());
    assert!(save_export_to(&dir, "../x.csv", "YSxi").is_err());
    assert!(save_export_limited(&dir, "x.csv", "YSxiYSxiYSxi", 4).unwrap_err().contains("too large"));
    assert!(!dir.exists() || fs::read_dir(&dir).unwrap().next().is_none(), "nothing may be written");
    assert!(MAX_EXPORT_BYTES >= 1024 * 1024);
}

#[test]
fn open_only_reaches_files_inside_the_export_folder() {
    let scratch = Scratch::new("open");
    let dir = export_dir(&scratch.0);
    save_export_to(&dir, "inside.txt", "YSxi").unwrap();
    fs::write(scratch.0.join("outside.txt"), b"secret").unwrap();

    assert!(existing_export(&dir, "inside.txt").is_ok());
    assert!(existing_export(&dir, "missing.txt").is_err());
    assert!(existing_export(&dir, "../../outside.txt").is_err());
    assert!(existing_export(&dir, "..\\..\\outside.txt").is_err());
    // a folder that has the name of an allowed file is not a file
    fs::create_dir_all(dir.join("folder.csv")).unwrap();
    assert!(existing_export(&dir, "folder.csv").is_err());
}

#[test]
fn notifications_are_trimmed_and_limited() {
    let (title, body) = clean_notification("  Payment due ", " 3 bookings expire today ").unwrap();
    assert_eq!((title.as_str(), body.as_str()), ("Payment due", "3 bookings expire today"));

    assert!(clean_notification("", "text").is_err());
    assert!(clean_notification("title", "  ").is_err());

    let (long_title, long_body) = clean_notification(&"t".repeat(500), &"b".repeat(2000)).unwrap();
    assert_eq!(long_title.chars().count(), 120);
    assert_eq!(long_body.chars().count(), 500);
    // quotes and shell syntax stay plain text: nothing is ever interpreted
    let (t, b) = clean_notification("a'; calc.exe; '", "`$(whoami)` \"x\"").unwrap();
    assert!(t.contains("calc.exe") && b.contains("whoami"));
}

#[test]
fn shell_info_reports_the_package_version_and_platform() {
    let info = get_shell_info();
    assert_eq!(info.version, env!("CARGO_PKG_VERSION"));
    assert_eq!(info.os, std::env::consts::OS);
    assert_eq!(info.arch, std::env::consts::ARCH);
}
