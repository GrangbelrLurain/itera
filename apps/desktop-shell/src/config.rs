//! Runtime configuration of the shell. The shell is thin: the only thing it needs to know is which web address to
//! load. Precedence: environment variable `ITERA_WEB_URL` > address baked in at build time
//! (`ITERA_DEFAULT_WEB_URL`, set by the installer build) > `http://localhost:3000`.

use serde::Serialize;
use tauri::Url;

/// Environment variable that points the shell at another web address (no rebuild needed).
pub const ENV_WEB_URL: &str = "ITERA_WEB_URL";

/// Address used when nothing is configured. The installer build can bake in the production address with the
/// `ITERA_DEFAULT_WEB_URL` environment variable.
pub const DEFAULT_WEB_URL: &str = match option_env!("ITERA_DEFAULT_WEB_URL") {
    Some(url) => url,
    None => "http://localhost:3000",
};

const FALLBACK_WEB_URL: &str = "http://localhost:3000";

/// The web address to load, plus a message for the splash when the configured value had to be ignored.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct WebTarget {
    pub url: String,
    pub warning: Option<String>,
}

/// Accepts only plain http(s) addresses with a host and no embedded credentials.
pub fn parse_web_url(raw: &str) -> Result<Url, String> {
    let url = Url::parse(raw.trim()).map_err(|e| format!("not a valid address: {e}"))?;
    if !matches!(url.scheme(), "http" | "https") {
        return Err("only http and https addresses are allowed".to_string());
    }
    if url.host_str().is_none() {
        return Err("the address has no host".to_string());
    }
    if !url.username().is_empty() || url.password().is_some() {
        return Err("user name and password in the address are not allowed".to_string());
    }
    Ok(url)
}

/// Resolves the target from the value of `ITERA_WEB_URL` (None or blank = not set). An invalid value falls back to
/// the default and is reported in `warning`, so a typo never leaves the user with a blank window.
pub fn resolve_web_target(env_value: Option<&str>) -> WebTarget {
    let default = parse_web_url(DEFAULT_WEB_URL)
        .or_else(|_| parse_web_url(FALLBACK_WEB_URL))
        .expect("the fallback address is valid");

    match env_value.map(str::trim).filter(|v| !v.is_empty()) {
        None => WebTarget { url: default.to_string(), warning: None },
        Some(raw) => match parse_web_url(raw) {
            Ok(url) => WebTarget { url: url.to_string(), warning: None },
            Err(reason) => WebTarget {
                url: default.to_string(),
                warning: Some(format!("{ENV_WEB_URL} was ignored ({reason}). Using {default}.")),
            },
        },
    }
}

/// Target from the process environment.
pub fn web_target_from_env() -> WebTarget {
    resolve_web_target(std::env::var(ENV_WEB_URL).ok().as_deref())
}

/// URL pattern of a whole origin, as used by capability `remote` entries: `http://host:port/*`.
pub fn origin_pattern(url: &Url) -> String {
    format!("{}/*", url.origin().ascii_serialization())
}

/// True for the pages the shell itself serves (splash): `tauri://localhost` or `http(s)://tauri.localhost`.
pub fn is_app_url(url: &Url) -> bool {
    url.scheme() == "tauri" || url.host_str() == Some("tauri.localhost")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unset_or_blank_uses_the_default() {
        assert_eq!(resolve_web_target(None).warning, None);
        assert_eq!(resolve_web_target(Some("   ")).url, resolve_web_target(None).url);
    }

    #[test]
    fn a_valid_value_wins_over_the_default() {
        let t = resolve_web_target(Some(" https://admin.example.com/app "));
        assert_eq!(t.url, "https://admin.example.com/app");
        assert_eq!(t.warning, None);
    }

    #[test]
    fn invalid_values_fall_back_and_explain() {
        for bad in ["not a url", "ftp://example.com", "file:///c:/x", "http://user:pw@example.com", "javascript:alert(1)"] {
            let t = resolve_web_target(Some(bad));
            assert_eq!(t.url, resolve_web_target(None).url, "{bad}");
            let warning = t.warning.unwrap_or_default();
            assert!(warning.contains(ENV_WEB_URL), "{bad}: {warning}");
        }
    }

    #[test]
    fn origin_pattern_keeps_port_and_drops_path() {
        let url = parse_web_url("http://localhost:3000/admin?x=1").unwrap();
        assert_eq!(origin_pattern(&url), "http://localhost:3000/*");
        let url = parse_web_url("https://admin.example.com/a/b").unwrap();
        assert_eq!(origin_pattern(&url), "https://admin.example.com/*");
    }

    #[test]
    fn app_urls_are_recognised() {
        assert!(is_app_url(&Url::parse("tauri://localhost/index.html").unwrap()));
        assert!(is_app_url(&Url::parse("http://tauri.localhost/").unwrap()));
        assert!(is_app_url(&Url::parse("https://tauri.localhost/").unwrap()));
        assert!(!is_app_url(&Url::parse("http://localhost:3000/").unwrap()));
        assert!(!is_app_url(&Url::parse("https://evil.example/tauri.localhost").unwrap()));
    }
}
