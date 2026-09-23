use std::env;
use std::fs;
use std::fs::File;
use std::io::{self, Write};
use std::process::{Command, ExitCode, Stdio};

const PROTOCOL_VERSION: u32 = 1;
const EXIT_INVALID_REQUEST: u8 = 64;
const EXIT_BACKEND_UNAVAILABLE: u8 = 69;
const EXIT_PERMISSION_DENIED: u8 = 77;
const EXIT_TIMEOUT: u8 = 124;
const EXIT_TRANSPORT_ERROR: u8 = 125;

const DEFAULT_HELPER: &str = "kf-adb-bridge";
const DEFAULT_SERIAL: &str = "kf-host-self";
const DEFAULT_CATALOG_PATH: &str = "/workspace/.kf/system/device-bridge-capabilities-v1.json";
const LIVE_PROBE_TIMEOUT_SECONDS: &str = "5";

#[derive(Debug)]
struct RuntimeStatus {
    backend: &'static str,
    lifecycle: String,
    identity: String,
    uid: Option<u32>,
    android_api: Option<u32>,
    source: &'static str,
    detail: String,
    exit_code: i32,
}

fn main() -> ExitCode {
    match run(env::args().skip(1).collect()) {
        Ok(code) => ExitCode::from(normalize_exit(code)),
        Err(error) => {
            eprintln!("kite-device: {error}");
            ExitCode::from(EXIT_INVALID_REQUEST)
        }
    }
}

fn run(mut args: Vec<String>) -> Result<i32, String> {
    let command = if args.is_empty() {
        "status".to_owned()
    } else {
        args.remove(0)
    };
    match command.as_str() {
        "status" => {
            let json = take_json_flag(&mut args)?;
            ensure_empty(&args, "status")?;
            let status = probe_runtime();
            print_status(&status, json)?;
            Ok(status.exit_code)
        }
        "capabilities" => {
            let json = take_json_flag(&mut args)?;
            ensure_empty(&args, "capabilities")?;
            let status = probe_runtime();
            print_capabilities(&status, json)?;
            Ok(status.exit_code)
        }
        "catalog" => {
            let json = take_json_flag(&mut args)?;
            ensure_empty(&args, "catalog")?;
            print_catalog(json)?;
            Ok(0)
        }
        "shell" | "exec-out" | "pull" | "push" | "install" | "uninstall" => {
            bridge_passthrough(&command, &args)
        }
        "adb" => bridge_adb(&args),
        "package" => run_package(args),
        "input" => run_input(args),
        "screen" => run_screen(args),
        "system" => run_system(args),
        "help" | "--help" | "-h" => {
            print_help();
            Ok(0)
        }
        "version" | "--version" | "-V" => {
            println!(
                "kite-device {} protocol={PROTOCOL_VERSION}",
                env!("CARGO_PKG_VERSION")
            );
            Ok(0)
        }
        unknown => Err(format!(
            "unknown command: {unknown}\nRun 'kite-device help' for usage."
        )),
    }
}

fn take_json_flag(args: &mut Vec<String>) -> Result<bool, String> {
    let mut json = false;
    args.retain(|arg| {
        if arg == "--json" {
            json = true;
            false
        } else {
            true
        }
    });
    if args.iter().any(|arg| arg.starts_with('-')) {
        return Err(format!("unsupported option: {}", args.join(" ")));
    }
    Ok(json)
}

fn ensure_empty(args: &[String], command: &str) -> Result<(), String> {
    if args.is_empty() {
        Ok(())
    } else {
        Err(format!(
            "{command} does not accept arguments: {}",
            args.join(" ")
        ))
    }
}

fn helper() -> String {
    env::var("KF_ADB_BRIDGE_HELPER").unwrap_or_else(|_| DEFAULT_HELPER.to_owned())
}

fn serial() -> String {
    env::var("KF_ADB_HOST_SELF_SERIAL").unwrap_or_else(|_| DEFAULT_SERIAL.to_owned())
}

fn base_bridge_command() -> Command {
    let mut command = Command::new(helper());
    command.arg("adb").arg("-s").arg(serial());
    command
}

fn bridge_passthrough(kind: &str, args: &[String]) -> Result<i32, String> {
    if matches!(
        kind,
        "shell" | "exec-out" | "pull" | "push" | "install" | "uninstall"
    ) && args.is_empty()
    {
        return Err(format!("{kind} requires arguments"));
    }
    let mut command = base_bridge_command();
    command.arg(kind).args(args);
    inherit_stdio(&mut command)
}

fn bridge_adb(args: &[String]) -> Result<i32, String> {
    if args.is_empty() {
        return Err("adb requires arguments".to_owned());
    }
    let mut command = base_bridge_command();
    command.args(args);
    inherit_stdio(&mut command)
}

fn bridge_shell_command(command_text: String) -> Result<i32, String> {
    let mut command = base_bridge_command();
    command.arg("shell").arg(command_text);
    inherit_stdio(&mut command)
}

fn bridge_exec_out_to_file(command_text: String, output_path: &str) -> Result<i32, String> {
    if output_path.trim().is_empty() {
        return Err("output path must not be empty".to_owned());
    }
    let output = File::create(output_path)
        .map_err(|error| format!("cannot create {output_path}: {error}"))?;
    let mut command = base_bridge_command();
    command
        .arg("exec-out")
        .arg(command_text)
        .stdin(Stdio::null())
        .stdout(Stdio::from(output))
        .stderr(Stdio::inherit());
    command
        .status()
        .map(|status| status.code().unwrap_or(i32::from(EXIT_TRANSPORT_ERROR)))
        .map_err(|error| format!("cannot start {}: {error}", helper()))
}

fn run_package(mut args: Vec<String>) -> Result<i32, String> {
    let action = take_action(&mut args, "package")?;
    match action.as_str() {
        "list" => {
            let flags = package_list_flags(&args)?;
            bridge_shell_command(format!("pm list packages{flags}"))
        }
        "inspect" => {
            let package = one_package(&args, "package inspect")?;
            bridge_shell_command(format!("dumpsys package {package}"))
        }
        "launch" => {
            let package = one_package(&args, "package launch")?;
            bridge_shell_command(format!(
                "monkey -p {package} -c android.intent.category.LAUNCHER 1"
            ))
        }
        "force-stop" => {
            let package = one_package(&args, "package force-stop")?;
            bridge_shell_command(format!("am force-stop {package}"))
        }
        "clear-data" => {
            let package = one_package(&args, "package clear-data")?;
            bridge_shell_command(format!("pm clear {package}"))
        }
        "permissions" => {
            let package = one_package(&args, "package permissions")?;
            bridge_shell_command(format!("dumpsys package {package}"))
        }
        "grant" | "revoke" => {
            ensure_count(
                &args,
                2,
                &format!("package {action} <package> <permission>"),
            )?;
            let package = package_name(&args[0])?;
            let permission = permission_name(&args[1])?;
            bridge_shell_command(format!("pm {action} {package} {permission}"))
        }
        "appops" => {
            let package = one_package(&args, "package appops")?;
            bridge_shell_command(format!("appops get {package}"))
        }
        "appops-set" => {
            ensure_count(&args, 3, "package appops-set <package> <operation> <mode>")?;
            let package = package_name(&args[0])?;
            let operation = app_op_name(&args[1])?;
            let mode = app_op_mode(&args[2])?;
            bridge_shell_command(format!("appops set {package} {operation} {mode}"))
        }
        "uninstall" => {
            let package = one_package(&args, "package uninstall")?;
            let mut command = base_bridge_command();
            command.arg("uninstall").arg(package);
            inherit_stdio(&mut command)
        }
        unknown => Err(format!("unknown package action: {unknown}")),
    }
}

fn run_input(mut args: Vec<String>) -> Result<i32, String> {
    let action = take_action(&mut args, "input")?;
    match action.as_str() {
        "tap" => {
            ensure_count(&args, 2, "input tap <x> <y>")?;
            let x = coordinate(&args[0])?;
            let y = coordinate(&args[1])?;
            bridge_shell_command(format!("input tap {x} {y}"))
        }
        "swipe" => {
            if !(args.len() == 4 || args.len() == 5) {
                return Err("usage: input swipe <x1> <y1> <x2> <y2> [duration-ms]".to_owned());
            }
            let values = args
                .iter()
                .map(|value| coordinate(value))
                .collect::<Result<Vec<_>, _>>()?;
            bridge_shell_command(format!("input swipe {}", values.join(" ")))
        }
        "text" => {
            if args.is_empty() {
                return Err("usage: input text <text...>".to_owned());
            }
            bridge_shell_command(format!("input text {}", shell_quote(&args.join(" "))))
        }
        "key" => {
            ensure_count(&args, 1, "input key <keycode>")?;
            let key = key_code(&args[0])?;
            bridge_shell_command(format!("input keyevent {key}"))
        }
        unknown => Err(format!("unknown input action: {unknown}")),
    }
}

fn run_screen(mut args: Vec<String>) -> Result<i32, String> {
    let action = take_action(&mut args, "screen")?;
    match action.as_str() {
        "capture" => {
            if args.len() > 1 {
                return Err("usage: screen capture [output.png]".to_owned());
            }
            if let Some(path) = args.first() {
                bridge_exec_out_to_file("screencap -p".to_owned(), path)
            } else {
                let mut command = base_bridge_command();
                command.arg("exec-out").arg("screencap -p");
                inherit_stdio(&mut command)
            }
        }
        "size" => {
            ensure_empty(&args, "screen size")?;
            bridge_shell_command("wm size; wm density".to_owned())
        }
        "record" => {
            if args.len() > 2 {
                return Err("usage: screen record [output.mp4] [duration-seconds]".to_owned());
            }
            let output = args.first().map(String::as_str);
            let duration = args
                .get(1)
                .map(|value| bounded_duration(value))
                .transpose()?;
            let mut command_text = String::from("screenrecord");
            if let Some(duration) = duration {
                command_text.push_str(&format!(" --time-limit {duration}"));
            }
            command_text.push_str(" --output-format=h264 -");
            match output {
                Some(path) => bridge_exec_out_to_file(command_text, path),
                None => {
                    let mut command = base_bridge_command();
                    command.arg("exec-out").arg(command_text);
                    inherit_stdio(&mut command)
                }
            }
        }
        unknown => Err(format!("unknown screen action: {unknown}")),
    }
}

fn run_system(mut args: Vec<String>) -> Result<i32, String> {
    let action = take_action(&mut args, "system")?;
    match action.as_str() {
        "info" => {
            ensure_empty(&args, "system info")?;
            bridge_shell_command(
                "printf 'manufacturer='; getprop ro.product.manufacturer; ".to_owned()
                    + "printf 'model='; getprop ro.product.model; "
                    + "printf 'device='; getprop ro.product.device; "
                    + "printf 'android='; getprop ro.build.version.release; "
                    + "printf 'api='; getprop ro.build.version.sdk; "
                    + "printf 'security_patch='; getprop ro.build.version.security_patch",
            )
        }
        "processes" => {
            ensure_empty(&args, "system processes")?;
            bridge_shell_command("ps -A".to_owned())
        }
        "logcat" => {
            let mut command = base_bridge_command();
            command.arg("logcat").args(args);
            inherit_stdio(&mut command)
        }
        "dumpsys" => {
            if args.len() > 1 {
                return Err("usage: system dumpsys [service]".to_owned());
            }
            let service = args.first().map(|value| service_name(value)).transpose()?;
            bridge_shell_command(match service {
                Some(service) => format!("dumpsys {service}"),
                None => "dumpsys".to_owned(),
            })
        }
        "settings-get" => {
            ensure_count(&args, 2, "system settings-get <system|secure|global> <key>")?;
            let namespace = settings_namespace(&args[0])?;
            let key = settings_key(&args[1])?;
            bridge_shell_command(format!("settings get {namespace} {key}"))
        }
        "settings-put" => {
            ensure_count(
                &args,
                3,
                "system settings-put <system|secure|global> <key> <value>",
            )?;
            let namespace = settings_namespace(&args[0])?;
            let key = settings_key(&args[1])?;
            bridge_shell_command(format!(
                "settings put {namespace} {key} {}",
                shell_quote(&args[2])
            ))
        }
        "settings-delete" => {
            ensure_count(
                &args,
                2,
                "system settings-delete <system|secure|global> <key>",
            )?;
            let namespace = settings_namespace(&args[0])?;
            let key = settings_key(&args[1])?;
            bridge_shell_command(format!("settings delete {namespace} {key}"))
        }
        unknown => Err(format!("unknown system action: {unknown}")),
    }
}

fn take_action(args: &mut Vec<String>, group: &str) -> Result<String, String> {
    if args.is_empty() {
        Err(format!("{group} requires an action"))
    } else {
        Ok(args.remove(0))
    }
}

fn ensure_count(args: &[String], count: usize, usage: &str) -> Result<(), String> {
    if args.len() == count {
        Ok(())
    } else {
        Err(format!("usage: {usage}"))
    }
}

fn one_package(args: &[String], usage: &str) -> Result<String, String> {
    ensure_count(args, 1, &format!("{usage} <package>"))?;
    package_name(&args[0])
}

fn package_name(value: &str) -> Result<String, String> {
    let valid = !value.is_empty()
        && value.len() <= 255
        && value.contains('.')
        && value
            .chars()
            .all(|character| character.is_ascii_alphanumeric() || matches!(character, '.' | '_'));
    if valid {
        Ok(value.to_owned())
    } else {
        Err(format!("invalid Android package name: {value}"))
    }
}

fn permission_name(value: &str) -> Result<String, String> {
    qualified_identifier(value, 255, "Android permission")
}

fn app_op_name(value: &str) -> Result<String, String> {
    qualified_identifier(value, 128, "AppOps operation")
}

fn qualified_identifier(value: &str, max_len: usize, label: &str) -> Result<String, String> {
    let valid = !value.is_empty()
        && value.len() <= max_len
        && value.chars().all(|character| {
            character.is_ascii_alphanumeric() || matches!(character, '_' | '-' | '.' | ':')
        });
    if valid {
        Ok(value.to_owned())
    } else {
        Err(format!("invalid {label}: {value}"))
    }
}

fn app_op_mode(value: &str) -> Result<&str, String> {
    match value {
        "allow" | "deny" | "ignore" | "default" | "foreground" => Ok(value),
        _ => Err(format!("invalid AppOps mode: {value}")),
    }
}

fn package_list_flags(args: &[String]) -> Result<String, String> {
    let mut flags = Vec::new();
    for argument in args {
        let flag = match argument.as_str() {
            "--system" => "-s",
            "--third-party" => "-3",
            "--disabled" => "-d",
            "--enabled" => "-e",
            "--include-path" => "-f",
            unknown => return Err(format!("unsupported package list option: {unknown}")),
        };
        flags.push(flag);
    }
    if flags.is_empty() {
        Ok(String::new())
    } else {
        Ok(format!(" {}", flags.join(" ")))
    }
}

fn coordinate(value: &str) -> Result<String, String> {
    value
        .parse::<u32>()
        .map(|number| number.to_string())
        .map_err(|_| format!("invalid non-negative coordinate or duration: {value}"))
}

fn bounded_duration(value: &str) -> Result<u32, String> {
    value
        .parse::<u32>()
        .ok()
        .filter(|seconds| (1..=180).contains(seconds))
        .ok_or_else(|| format!("duration must be between 1 and 180 seconds: {value}"))
}

fn key_code(value: &str) -> Result<String, String> {
    let normalized = value.to_ascii_uppercase();
    let valid = !normalized.is_empty()
        && normalized.len() <= 64
        && normalized
            .chars()
            .all(|character| character.is_ascii_alphanumeric() || character == '_');
    if valid {
        Ok(normalized)
    } else {
        Err(format!("invalid key code: {value}"))
    }
}

fn service_name(value: &str) -> Result<String, String> {
    let valid = !value.is_empty()
        && value.len() <= 96
        && value.chars().all(|character| {
            character.is_ascii_alphanumeric() || matches!(character, '_' | '-' | '.')
        });
    if valid {
        Ok(value.to_owned())
    } else {
        Err(format!("invalid Android service name: {value}"))
    }
}

fn settings_namespace(value: &str) -> Result<&str, String> {
    match value {
        "system" | "secure" | "global" => Ok(value),
        _ => Err(format!("invalid settings namespace: {value}")),
    }
}

fn settings_key(value: &str) -> Result<String, String> {
    let valid = !value.is_empty()
        && value.len() <= 128
        && value.chars().all(|character| {
            character.is_ascii_alphanumeric() || matches!(character, '_' | '-' | '.')
        });
    if valid {
        Ok(value.to_owned())
    } else {
        Err(format!("invalid settings key: {value}"))
    }
}

fn shell_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', "'\\''"))
}

fn inherit_stdio(command: &mut Command) -> Result<i32, String> {
    command
        .stdin(Stdio::inherit())
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit())
        .status()
        .map(|status| status.code().unwrap_or(i32::from(EXIT_TRANSPORT_ERROR)))
        .map_err(|error| format!("cannot start {}: {error}", helper()))
}

fn probe_runtime() -> RuntimeStatus {
    let mut command = base_bridge_command();
    command
        .arg("shell")
        .arg("printf 'uid='; id -u; printf 'api='; getprop ro.build.version.sdk")
        .env(
            "KF_ADB_BRIDGE_REQUEST_TIMEOUT_SEC",
            LIVE_PROBE_TIMEOUT_SECONDS,
        )
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());

    match command.output() {
        Ok(output) => {
            let stdout = String::from_utf8_lossy(&output.stdout);
            let stderr = String::from_utf8_lossy(&output.stderr).trim().to_owned();
            let exit_code = output
                .status
                .code()
                .unwrap_or(i32::from(EXIT_TRANSPORT_ERROR));
            if output.status.success() {
                let uid = parse_line_number(&stdout, "uid=");
                let android_api = parse_line_number(&stdout, "api=");
                let identity = match uid {
                    Some(0) => "root",
                    Some(2000) => "shell",
                    Some(_) => "unknown",
                    None => "unknown",
                };
                RuntimeStatus {
                    backend: selected_backend(uid),
                    lifecycle: "ready".to_owned(),
                    identity: identity.to_owned(),
                    uid,
                    android_api,
                    source: "live_probe",
                    detail: String::new(),
                    exit_code: 0,
                }
            } else {
                unavailable_status(exit_code, stderr)
            }
        }
        Err(error) => RuntimeStatus {
            backend: "none",
            lifecycle: "transport_unavailable".to_owned(),
            identity: "unknown".to_owned(),
            uid: None,
            android_api: None,
            source: "live_probe",
            detail: error.to_string(),
            exit_code: i32::from(EXIT_TRANSPORT_ERROR),
        },
    }
}

fn unavailable_status(exit_code: i32, detail: String) -> RuntimeStatus {
    let env_status = env::var("KF_ADB_BRIDGE_STATUS").unwrap_or_default();
    let permission = env::var("KF_ADB_SHIZUKU_PERMISSION").unwrap_or_default();
    let lifecycle = match exit_code {
        code if code == i32::from(EXIT_PERMISSION_DENIED) => "permission_required",
        code if code == i32::from(EXIT_BACKEND_UNAVAILABLE) => "backend_unavailable",
        code if code == i32::from(EXIT_TIMEOUT) => "transport_timeout",
        _ if permission == "required" || permission == "denied" => "permission_required",
        _ if !env_status.is_empty() => env_status.as_str(),
        _ => "failed",
    };
    RuntimeStatus {
        backend: if lifecycle == "permission_required" {
            "shizuku"
        } else {
            "none"
        },
        lifecycle: lifecycle.to_owned(),
        identity: "unknown".to_owned(),
        uid: None,
        android_api: None,
        source: "live_probe",
        detail,
        exit_code,
    }
}

fn selected_backend(uid: Option<u32>) -> &'static str {
    selected_backend_from(env::var("KF_DEVICE_SELECTED_BACKEND").as_deref().ok(), uid)
}

fn selected_backend_from(projected: Option<&str>, uid: Option<u32>) -> &'static str {
    match projected {
        Some("root_experimental") if uid == Some(0) => "root_experimental",
        _ => "shizuku",
    }
}

fn parse_line_number(text: &str, prefix: &str) -> Option<u32> {
    text.lines()
        .find_map(|line| line.trim().strip_prefix(prefix))
        .and_then(|value| value.trim().parse().ok())
}

fn print_status(status: &RuntimeStatus, json: bool) -> Result<(), String> {
    if json {
        println!("{}", status_json(status));
    } else {
        println!("backend={}", status.backend);
        println!("lifecycle={}", status.lifecycle);
        println!("identity={}", status.identity);
        println!("uid={}", optional_number(status.uid));
        println!("android_api={}", optional_number(status.android_api));
        println!("source={}", status.source);
        if !status.detail.is_empty() {
            println!("detail={}", status.detail);
        }
    }
    io::stdout().flush().map_err(|error| error.to_string())
}

fn print_capabilities(status: &RuntimeStatus, json: bool) -> Result<(), String> {
    let catalog = read_catalog()?;
    let transport = if status.exit_code == 0 {
        implemented_capability_ids()
    } else {
        Vec::new()
    };
    if json {
        let transport_json = transport
            .iter()
            .map(|id| format!("\"{}\"", json_escape(id)))
            .collect::<Vec<_>>()
            .join(",");
        println!(
            "{{\"protocolVersion\":{PROTOCOL_VERSION},\"runtime\":{},\"transportCapabilityIds\":[{transport_json}],\"authorization\":{{\"state\":\"caller_policy_required\",\"scope\":\"agent_session\"}},\"catalog\":{}}}",
            status_json(status),
            catalog.trim()
        );
    } else {
        print_status(status, false)?;
        println!("transport_capabilities={}", transport.join(","));
        println!("authorization=caller_policy_required");
        println!("catalog={}", catalog_path());
    }
    Ok(())
}

fn print_catalog(json: bool) -> Result<(), String> {
    let catalog = read_catalog()?;
    if json {
        print!("{}", catalog);
        if !catalog.ends_with('\n') {
            println!();
        }
    } else {
        println!("catalog={}", catalog_path());
        println!("protocol={PROTOCOL_VERSION}");
    }
    Ok(())
}

fn read_catalog() -> Result<String, String> {
    fs::read_to_string(catalog_path())
        .map_err(|error| format!("cannot read capability catalog: {error}"))
}

fn catalog_path() -> String {
    env::var("KF_DEVICE_CAPABILITY_CATALOG_PATH")
        .unwrap_or_else(|_| DEFAULT_CATALOG_PATH.to_owned())
}

fn implemented_capability_ids() -> Vec<String> {
    parse_capability_ids(&env::var("KF_DEVICE_IMPLEMENTED_CAPABILITIES").unwrap_or_default())
}

fn parse_capability_ids(value: &str) -> Vec<String> {
    let mut ids = value
        .split(',')
        .map(str::trim)
        .filter(|id| !id.is_empty())
        .map(str::to_owned)
        .collect::<Vec<_>>();
    ids.sort();
    ids.dedup();
    ids
}

fn status_json(status: &RuntimeStatus) -> String {
    format!(
        "{{\"backend\":\"{}\",\"lifecycle\":\"{}\",\"identity\":\"{}\",\"uid\":{},\"androidApi\":{},\"source\":\"{}\",\"detail\":\"{}\",\"exitCode\":{}}}",
        json_escape(status.backend),
        json_escape(&status.lifecycle),
        json_escape(&status.identity),
        optional_json_number(status.uid),
        optional_json_number(status.android_api),
        json_escape(status.source),
        json_escape(&status.detail),
        status.exit_code
    )
}

fn json_escape(value: &str) -> String {
    let mut escaped = String::with_capacity(value.len());
    for character in value.chars() {
        match character {
            '"' => escaped.push_str("\\\""),
            '\\' => escaped.push_str("\\\\"),
            '\n' => escaped.push_str("\\n"),
            '\r' => escaped.push_str("\\r"),
            '\t' => escaped.push_str("\\t"),
            control if control.is_control() => {
                use std::fmt::Write as _;
                let _ = write!(escaped, "\\u{:04x}", control as u32);
            }
            other => escaped.push(other),
        }
    }
    escaped
}

fn optional_number(value: Option<u32>) -> String {
    value.map(|number| number.to_string()).unwrap_or_default()
}

fn optional_json_number(value: Option<u32>) -> String {
    value
        .map(|number| number.to_string())
        .unwrap_or_else(|| "null".to_owned())
}

fn normalize_exit(code: i32) -> u8 {
    if (0..=255).contains(&code) {
        code as u8
    } else {
        EXIT_TRANSPORT_ERROR
    }
}

fn print_help() {
    println!(
        "Kite Device Bridge {version}\n\
Usage:\n\
  kite-device status [--json]\n\
  kite-device capabilities [--json]\n\
  kite-device catalog [--json]\n\
  kite-device shell <command...>\n\
  kite-device exec-out <command...>\n\
  kite-device pull <remote> [local]\n\
  kite-device push <local> <remote>\n\
  kite-device install [flags] <apk>\n\
  kite-device uninstall [flags] <package>\n\
  kite-device adb <adb arguments...>\n\
  kite-device package <list|inspect|launch|force-stop|clear-data|uninstall> ...\n\
  kite-device package <permissions|grant|revoke|appops|appops-set> ...\n\
  kite-device input <tap|swipe|text|key> ...\n\
  kite-device screen <capture|record|size> ...\n\
  kite-device system <info|processes|logcat|dumpsys|settings-get|settings-put|settings-delete> ...\n\
\nAll operations use the kf-host-self transport. Capability discovery reports transport readiness; the caller must apply the current Agent-session authorization policy.",
        version = env!("CARGO_PKG_VERSION")
    );
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_probe_output() {
        let output = "uid=2000\napi=34\n";
        assert_eq!(parse_line_number(output, "uid="), Some(2000));
        assert_eq!(parse_line_number(output, "api="), Some(34));
    }

    #[test]
    fn root_backend_requires_explicit_projection_and_uid_zero() {
        assert_eq!(
            selected_backend_from(Some("root_experimental"), Some(0)),
            "root_experimental"
        );
        assert_eq!(
            selected_backend_from(Some("root_experimental"), Some(2000)),
            "shizuku"
        );
        assert_eq!(selected_backend_from(None, Some(0)), "shizuku");
    }

    #[test]
    fn escapes_json_control_characters() {
        assert_eq!(json_escape("a\"b\\c\n"), "a\\\"b\\\\c\\n");
    }

    #[test]
    fn implemented_capabilities_are_normalized_from_android_projection() {
        assert_eq!(
            parse_capability_ids("shell.exec, package.list,shell.exec,,"),
            vec!["package.list".to_owned(), "shell.exec".to_owned()]
        );
    }

    #[test]
    fn rejects_shell_metacharacters_in_structured_identifiers() {
        assert!(package_name("com.example.safe").is_ok());
        assert!(package_name("com.example;reboot").is_err());
        assert!(settings_key("screen_brightness").is_ok());
        assert!(settings_key("name;id").is_err());
        assert!(permission_name("android.permission.CAMERA").is_ok());
        assert!(permission_name("android.permission.CAMERA;reboot").is_err());
        assert!(app_op_name("android:camera").is_ok());
        assert!(app_op_mode("allow").is_ok());
        assert!(app_op_mode("always").is_err());
    }

    #[test]
    fn maps_package_list_options_through_an_allowlist() {
        assert_eq!(
            package_list_flags(&["--third-party".to_owned(), "--disabled".to_owned()]).unwrap(),
            " -3 -d"
        );
        assert!(package_list_flags(&["--user=0".to_owned()]).is_err());
    }

    #[test]
    fn bounds_screen_record_duration() {
        assert_eq!(bounded_duration("1").unwrap(), 1);
        assert_eq!(bounded_duration("180").unwrap(), 180);
        assert!(bounded_duration("0").is_err());
        assert!(bounded_duration("181").is_err());
    }
}
