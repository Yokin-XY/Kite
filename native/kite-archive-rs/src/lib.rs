use flate2::read::GzDecoder;
use jni::EnvUnowned;
use jni::errors::{Result as JniResult, ThrowRuntimeExAndDefault};
use jni::objects::{JObject, JString};
use jni::{JValue, jni_sig, jni_str};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::HashSet;
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};
use tar::Archive as TarArchive;
use xz2::read::XzDecoder;
use zip::ZipArchive;

const BUFFER_SIZE: usize = 64 * 1024;
const MAXIMUM_PATH_BYTES: usize = 4_096;
const UNIX_TYPE_MASK: u32 = 0xF000;
const UNIX_REGULAR_FILE: u32 = 0x8000;
const PROGRESS_BYTES: u64 = 1024 * 1024;
const PROGRESS_ENTRIES: usize = 128;
const PROGRESS_INTERVAL: Duration = Duration::from_millis(250);

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ExtractRequest {
    source: String,
    destination: String,
    staging_directory: String,
    maximum_archive_bytes: u64,
    maximum_entries: usize,
    maximum_total_bytes: u64,
    maximum_file_bytes: u64,
    maximum_depth: usize,
    maximum_expansion_ratio: u64,
    reuse_key: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct TarExtractRequest {
    source: String,
    destination: String,
    staging_directory: String,
    maximum_archive_bytes: u64,
    maximum_entries: usize,
    maximum_total_bytes: u64,
    maximum_file_bytes: u64,
    maximum_depth: usize,
    maximum_expansion_ratio: u64,
    expected_archive_bytes: Option<u64>,
    expected_sha256: Option<String>,
    compression: String,
    special_entry_policy: String,
    reuse_key: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ExtractResponse {
    status: &'static str,
    reason: Option<String>,
    entries_extracted: usize,
    bytes_extracted: u64,
}

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct CompletionMarker {
    schema_version: u32,
    reuse_key: String,
    entries_extracted: usize,
    bytes_extracted: u64,
}

impl ExtractResponse {
    fn success(entries: usize, bytes: u64) -> Self {
        Self {
            status: "success",
            reason: None,
            entries_extracted: entries,
            bytes_extracted: bytes,
        }
    }

    fn failure(reason: impl Into<String>) -> Self {
        Self {
            status: "failure",
            reason: Some(reason.into()),
            entries_extracted: 0,
            bytes_extracted: 0,
        }
    }

    fn cancelled(entries: usize, bytes: u64) -> Self {
        Self {
            status: "cancelled",
            reason: None,
            entries_extracted: entries,
            bytes_extracted: bytes,
        }
    }
}

#[derive(Clone)]
struct SafeEntry {
    name: String,
    relative_path: PathBuf,
    directory: bool,
    declared_size: u64,
}

struct DeferredTarHardLink {
    destination: PathBuf,
    target: PathBuf,
    mode: u32,
}

#[derive(Debug)]
enum ExtractError {
    Failed(&'static str),
    FailedDetail(String),
    Cancelled { entries: usize, bytes: u64 },
}

type ExtractResult<T> = Result<T, ExtractError>;
type ProgressObserver<'a> = dyn FnMut(usize, u64) -> Result<bool, ()> + 'a;

struct ProgressGate {
    last_entries: usize,
    last_bytes: u64,
    last_emit: Instant,
    emitted: bool,
}

impl ProgressGate {
    fn new() -> Self {
        Self {
            last_entries: 0,
            last_bytes: 0,
            last_emit: Instant::now(),
            emitted: false,
        }
    }

    fn poll(
        &mut self,
        observer: &mut ProgressObserver<'_>,
        entries: usize,
        bytes: u64,
        force: bool,
    ) -> ExtractResult<()> {
        let now = Instant::now();
        let should_emit = force
            || !self.emitted
            || entries.saturating_sub(self.last_entries) >= PROGRESS_ENTRIES
            || bytes.saturating_sub(self.last_bytes) >= PROGRESS_BYTES
            || now.duration_since(self.last_emit) >= PROGRESS_INTERVAL;
        if !should_emit {
            return Ok(());
        }
        let keep_running = observer(entries, bytes)
            .map_err(|_| ExtractError::Failed("rust_archive_callback_failed"))?;
        if !keep_running {
            return Err(ExtractError::Cancelled { entries, bytes });
        }
        self.last_entries = entries;
        self.last_bytes = bytes;
        self.last_emit = now;
        self.emitted = true;
        Ok(())
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kite_app_foundation_runtime_RustArchiveBridge_extractZipNative<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _receiver: JObject<'caller>,
    request: JString<'caller>,
    observer: JObject<'caller>,
) -> JString<'caller> {
    unowned_env
        .with_env(|env| -> JniResult<_> {
            let response = {
                let mut callback = |entries: usize, bytes: u64| {
                    env.call_method(
                        &observer,
                        jni_str!("onArchiveProgress"),
                        jni_sig!("(IJ)Z"),
                        &[JValue::Int(entries as i32), JValue::Long(bytes as i64)],
                    )
                    .and_then(|value| value.z())
                    .map_err(|_| ())
                };
                execute_json(&request.to_string(), &mut callback)
            };
            JString::from_str(env, response)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kite_app_foundation_runtime_RustArchiveBridge_extractTarNative<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _receiver: JObject<'caller>,
    request: JString<'caller>,
    observer: JObject<'caller>,
) -> JString<'caller> {
    unowned_env
        .with_env(|env| -> JniResult<_> {
            let response = {
                let mut callback = |entries: usize, bytes: u64| {
                    env.call_method(
                        &observer,
                        jni_str!("onArchiveProgress"),
                        jni_sig!("(IJ)Z"),
                        &[JValue::Int(entries as i32), JValue::Long(bytes as i64)],
                    )
                    .and_then(|value| value.z())
                    .map_err(|_| ())
                };
                execute_tar_json(&request.to_string(), &mut callback)
            };
            JString::from_str(env, response)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

fn execute_json(raw: &str, observer: &mut ProgressObserver<'_>) -> String {
    let request = match serde_json::from_str::<ExtractRequest>(raw) {
        Ok(request) => request,
        Err(_) => return response_json(ExtractResponse::failure("rust_archive_request_invalid")),
    };
    response_json(execute(&request, observer))
}

fn execute_tar_json(raw: &str, observer: &mut ProgressObserver<'_>) -> String {
    let request = match serde_json::from_str::<TarExtractRequest>(raw) {
        Ok(request) => request,
        Err(_) => return response_json(ExtractResponse::failure("rust_archive_request_invalid")),
    };
    response_json(execute_tar(&request, observer))
}

fn response_json(response: ExtractResponse) -> String {
    serde_json::to_string(&response).unwrap_or_else(|_| {
        "{\"status\":\"failure\",\"reason\":\"rust_archive_response_failed\",\"entriesExtracted\":0,\"bytesExtracted\":0}".to_owned()
    })
}

fn execute(request: &ExtractRequest, observer: &mut ProgressObserver<'_>) -> ExtractResponse {
    let staging = Path::new(&request.staging_directory);
    let destination = Path::new(&request.destination);
    if let Some(reused) = reuse_existing(destination, request.reuse_key.as_deref()) {
        return reused;
    }
    let result = extract(request, observer);
    match result {
        Ok((entries, bytes)) => ExtractResponse::success(entries, bytes),
        Err(ExtractError::Cancelled { entries, bytes }) => {
            let _ = remove_owned_tree(staging);
            ExtractResponse::cancelled(entries, bytes)
        }
        Err(ExtractError::Failed(reason)) => {
            let _ = remove_owned_tree(staging);
            if destination.exists() {
                ExtractResponse::failure("rust_archive_destination_changed")
            } else {
                ExtractResponse::failure(reason)
            }
        }
        Err(ExtractError::FailedDetail(reason)) => {
            let _ = remove_owned_tree(staging);
            if destination.exists() {
                ExtractResponse::failure("rust_archive_destination_changed")
            } else {
                ExtractResponse::failure(reason)
            }
        }
    }
}

fn execute_tar(
    request: &TarExtractRequest,
    observer: &mut ProgressObserver<'_>,
) -> ExtractResponse {
    let staging = Path::new(&request.staging_directory);
    let destination = Path::new(&request.destination);
    if let Some(reused) = reuse_existing(destination, request.reuse_key.as_deref()) {
        return reused;
    }
    match extract_tar(request, observer) {
        Ok((entries, bytes)) => ExtractResponse::success(entries, bytes),
        Err(ExtractError::Cancelled { entries, bytes }) => {
            let _ = remove_owned_tree(staging);
            ExtractResponse::cancelled(entries, bytes)
        }
        Err(ExtractError::Failed(reason)) => {
            let _ = remove_owned_tree(staging);
            if destination.exists() {
                ExtractResponse::failure("rust_archive_destination_changed")
            } else {
                ExtractResponse::failure(reason)
            }
        }
        Err(ExtractError::FailedDetail(reason)) => {
            let _ = remove_owned_tree(staging);
            if destination.exists() {
                ExtractResponse::failure("rust_archive_destination_changed")
            } else {
                ExtractResponse::failure(reason)
            }
        }
    }
}

fn extract(
    request: &ExtractRequest,
    observer: &mut ProgressObserver<'_>,
) -> ExtractResult<(usize, u64)> {
    let source = Path::new(&request.source);
    let destination = Path::new(&request.destination);
    let staging = Path::new(&request.staging_directory);
    let source_bytes = validate_paths(source, destination, staging, request.maximum_archive_bytes)?;
    remove_owned_tree(staging)
        .map_err(|_| ExtractError::Failed("native_archive_staging_cleanup_failed"))?;
    fs::create_dir(staging)
        .map_err(|_| ExtractError::Failed("native_archive_staging_create_failed"))?;

    let source_file =
        File::open(source).map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
    let mut archive = ZipArchive::new(source_file)
        .map_err(|_| ExtractError::Failed("native_archive_entry_unreadable"))?;
    let mut progress = ProgressGate::new();
    progress.poll(observer, 0, 0, true)?;
    let safe_entries = preflight(&mut archive, request, &mut progress, observer)?;
    let mut total_bytes = 0_u64;
    let mut entries_extracted = 0_usize;
    let mut buffer = vec![0_u8; BUFFER_SIZE];

    for (index, expected) in safe_entries.iter().enumerate() {
        progress.poll(observer, entries_extracted, total_bytes, false)?;
        let mut entry = archive
            .by_index(index)
            .map_err(|_| ExtractError::Failed("native_archive_entry_unreadable"))?;
        if entry.name() != expected.name || entry.is_dir() != expected.directory {
            return Err(ExtractError::Failed(
                "native_archive_central_directory_mismatch",
            ));
        }
        entries_extracted += 1;
        let output = staging.join(&expected.relative_path);
        if !output.starts_with(staging) {
            return Err(ExtractError::Failed("native_archive_path_invalid"));
        }
        if expected.directory {
            fs::create_dir_all(&output)
                .map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
            progress.poll(observer, entries_extracted, total_bytes, false)?;
            continue;
        }
        let parent = output
            .parent()
            .ok_or(ExtractError::Failed("native_archive_path_invalid"))?;
        fs::create_dir_all(parent)
            .map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
        let mut target = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&output)
            .map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
        let mut file_bytes = 0_u64;
        loop {
            progress.poll(observer, entries_extracted, total_bytes, false)?;
            let count = entry
                .read(&mut buffer)
                .map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
            if count == 0 {
                break;
            }
            file_bytes = file_bytes
                .checked_add(count as u64)
                .ok_or(ExtractError::Failed("native_archive_file_size_limit"))?;
            total_bytes = total_bytes
                .checked_add(count as u64)
                .ok_or(ExtractError::Failed("native_archive_total_size_limit"))?;
            if file_bytes > request.maximum_file_bytes {
                return Err(ExtractError::Failed("native_archive_file_size_limit"));
            }
            if total_bytes > request.maximum_total_bytes {
                return Err(ExtractError::Failed("native_archive_total_size_limit"));
            }
            let expansion_limit = request
                .maximum_expansion_ratio
                .checked_mul(source_bytes)
                .unwrap_or(u64::MAX);
            if total_bytes > expansion_limit {
                return Err(ExtractError::Failed("native_archive_expansion_ratio_limit"));
            }
            target
                .write_all(&buffer[..count])
                .map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
        }
        target
            .flush()
            .map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
        if file_bytes != expected.declared_size {
            return Err(ExtractError::Failed(
                "native_archive_central_directory_mismatch",
            ));
        }
    }

    progress.poll(observer, entries_extracted, total_bytes, true)?;
    write_completion_marker(
        staging,
        request.reuse_key.as_deref(),
        entries_extracted,
        total_bytes,
    )?;
    fs::rename(staging, destination)
        .map_err(|_| ExtractError::Failed("native_archive_atomic_move_unsupported"))?;
    Ok((entries_extracted, total_bytes))
}

fn extract_tar(
    request: &TarExtractRequest,
    observer: &mut ProgressObserver<'_>,
) -> ExtractResult<(usize, u64)> {
    let source = Path::new(&request.source);
    let destination = Path::new(&request.destination);
    let staging = Path::new(&request.staging_directory);
    let source_bytes = validate_paths(source, destination, staging, request.maximum_archive_bytes)?;
    let mut progress = ProgressGate::new();
    progress.poll(observer, 0, 0, true)?;
    verify_artifact(
        source,
        source_bytes,
        request.expected_archive_bytes,
        request.expected_sha256.as_deref(),
        &mut progress,
        observer,
    )?;
    remove_owned_tree(staging)
        .map_err(|_| ExtractError::Failed("native_archive_staging_cleanup_failed"))?;
    fs::create_dir(staging)
        .map_err(|_| ExtractError::Failed("native_archive_staging_create_failed"))?;

    let source_file =
        File::open(source).map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
    let decoder: Box<dyn Read> = match request.compression.as_str() {
        "none" => Box::new(source_file),
        "gzip" => Box::new(GzDecoder::new(source_file)),
        "xz" => Box::new(XzDecoder::new(source_file)),
        _ => {
            return Err(ExtractError::Failed(
                "native_archive_compression_unsupported",
            ));
        }
    };
    let mut archive = TarArchive::new(decoder);
    archive.set_preserve_permissions(false);
    archive.set_preserve_ownerships(false);
    archive.set_preserve_mtime(false);
    archive.set_unpack_xattrs(false);

    let mut total_bytes = 0_u64;
    let mut entries_extracted = 0_usize;
    let mut seen = HashSet::new();
    let mut directory_modes = Vec::new();
    let mut deferred_hard_links = Vec::new();
    let entries = archive
        .entries()
        .map_err(|_| ExtractError::Failed("native_archive_entry_unreadable"))?;
    for entry in entries {
        progress.poll(observer, entries_extracted, total_bytes, false)?;
        if entries_extracted >= request.maximum_entries {
            return Err(ExtractError::Failed("native_archive_entry_limit"));
        }
        let mut entry =
            entry.map_err(|_| ExtractError::Failed("native_archive_entry_unreadable"))?;
        let path_bytes = entry.path_bytes();
        let raw_path = std::str::from_utf8(path_bytes.as_ref())
            .map_err(|_| ExtractError::Failed("native_archive_path_encoding_invalid"))?;
        let relative_path = safe_tar_relative_path(raw_path, request.maximum_depth)?;
        if relative_path.as_os_str().is_empty() {
            entries_extracted += 1;
            continue;
        }
        let key = relative_path.to_string_lossy().into_owned();
        if !seen.insert(key) {
            return Err(ExtractError::Failed("native_archive_duplicate_entry"));
        }
        entries_extracted += 1;

        let entry_type = entry.header().entry_type();
        let declared_size = entry
            .header()
            .size()
            .map_err(|_| ExtractError::Failed("native_archive_entry_unreadable"))?;
        if declared_size > request.maximum_file_bytes {
            return Err(ExtractError::Failed("native_archive_file_size_limit"));
        }
        let declared_total = total_bytes
            .checked_add(declared_size)
            .ok_or(ExtractError::Failed("native_archive_total_size_limit"))?;
        if declared_total > request.maximum_total_bytes {
            return Err(ExtractError::Failed("native_archive_total_size_limit"));
        }
        let expansion_limit = request
            .maximum_expansion_ratio
            .checked_mul(source_bytes)
            .unwrap_or(u64::MAX);
        if declared_total > expansion_limit {
            return Err(ExtractError::Failed("native_archive_expansion_ratio_limit"));
        }

        if entry_type.is_character_special()
            || entry_type.is_block_special()
            || entry_type.is_fifo()
        {
            match request.special_entry_policy.as_str() {
                "skip" => continue,
                "materialize_empty_file" => {
                    let mode = entry.header().mode().unwrap_or(0o600) & 0o777;
                    materialize_tar_special_entry(staging, &relative_path, mode)?;
                    continue;
                }
                "reject" => return Err(ExtractError::Failed("native_archive_special_entry")),
                _ => {
                    return Err(ExtractError::Failed(
                        "native_archive_special_entry_policy_invalid",
                    ));
                }
            }
        }

        if entry_type.is_hard_link() {
            let target_name = entry
                .link_name()
                .map_err(|_| ExtractError::Failed("native_archive_link_target_invalid"))?
                .ok_or(ExtractError::Failed("native_archive_link_target_invalid"))?;
            let raw_target = target_name
                .to_str()
                .ok_or(ExtractError::Failed("native_archive_path_encoding_invalid"))?;
            let target = safe_tar_relative_path(raw_target, request.maximum_depth)?;
            if target.as_os_str().is_empty() {
                return Err(ExtractError::Failed("native_archive_link_target_invalid"));
            }
            let mode = entry.header().mode().unwrap_or(0o755) & 0o777;
            if !materialize_tar_hard_link(staging, &relative_path, &target, mode)? {
                deferred_hard_links.push(DeferredTarHardLink {
                    destination: relative_path,
                    target,
                    mode,
                });
            }
            continue;
        }

        if entry_type.is_dir() {
            let output = staging.join(&relative_path);
            fs::create_dir_all(&output).map_err(|error| {
                ExtractError::FailedDetail(format!(
                    "native_archive_directory_failure:{}:{}",
                    relative_path.display(),
                    error.kind()
                ))
            })?;
            let mode = entry.header().mode().unwrap_or(0o755) & 0o777;
            directory_modes.push((output, mode));
            continue;
        }

        if entry_type.is_file() {
            write_tar_regular_file(
                staging,
                &relative_path,
                &mut entry,
                declared_size,
                request,
                source_bytes,
                &mut total_bytes,
                entries_extracted,
                &mut progress,
                observer,
            )?;
        } else if !entry.unpack_in(staging).map_err(|error| {
            ExtractError::FailedDetail(format!(
                "native_archive_unpack_failure:{}:{}",
                relative_path.display(),
                error.kind()
            ))
        })? {
            return Err(ExtractError::Failed("native_archive_path_invalid"));
        }
    }

    materialize_deferred_tar_hard_links(staging, deferred_hard_links)?;

    directory_modes.sort_by(|left, right| {
        right
            .0
            .components()
            .count()
            .cmp(&left.0.components().count())
    });
    for (directory, mode) in directory_modes {
        apply_directory_mode(&directory, mode)?;
    }
    progress.poll(observer, entries_extracted, total_bytes, true)?;
    write_completion_marker(
        staging,
        request.reuse_key.as_deref(),
        entries_extracted,
        total_bytes,
    )?;
    fs::rename(staging, destination)
        .map_err(|_| ExtractError::Failed("native_archive_atomic_move_unsupported"))?;
    Ok((entries_extracted, total_bytes))
}

fn reuse_existing(destination: &Path, reuse_key: Option<&str>) -> Option<ExtractResponse> {
    if !destination.exists() {
        return None;
    }
    let key = reuse_key?;
    let marker = fs::read_to_string(destination.join(".kite-archive-ready")).ok()?;
    let marker = serde_json::from_str::<CompletionMarker>(&marker).ok()?;
    if marker.schema_version != 1 || marker.reuse_key != key {
        return None;
    }
    Some(ExtractResponse::success(
        marker.entries_extracted,
        marker.bytes_extracted,
    ))
}

fn write_completion_marker(
    staging: &Path,
    reuse_key: Option<&str>,
    entries: usize,
    bytes: u64,
) -> ExtractResult<()> {
    let Some(key) = reuse_key else {
        return Ok(());
    };
    if key.is_empty() || key.len() > 160 || !key.bytes().all(|byte| byte.is_ascii_graphic()) {
        return Err(ExtractError::Failed("native_archive_reuse_key_invalid"));
    }
    let marker = CompletionMarker {
        schema_version: 1,
        reuse_key: key.to_owned(),
        entries_extracted: entries,
        bytes_extracted: bytes,
    };
    let payload = serde_json::to_vec(&marker)
        .map_err(|_| ExtractError::Failed("native_archive_marker_failed"))?;
    let marker_path = staging.join(".kite-archive-ready");
    let mut file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(marker_path)
        .map_err(|_| ExtractError::Failed("native_archive_marker_failed"))?;
    file.write_all(&payload)
        .and_then(|_| file.sync_all())
        .map_err(|_| ExtractError::Failed("native_archive_marker_failed"))
}

#[allow(clippy::too_many_arguments)]
fn write_tar_regular_file<R: Read>(
    staging: &Path,
    relative_path: &Path,
    entry: &mut tar::Entry<'_, R>,
    declared_size: u64,
    request: &TarExtractRequest,
    source_bytes: u64,
    total_bytes: &mut u64,
    entries_extracted: usize,
    progress: &mut ProgressGate,
    observer: &mut ProgressObserver<'_>,
) -> ExtractResult<()> {
    let output = staging.join(relative_path);
    let parent = output
        .parent()
        .ok_or(ExtractError::Failed("native_archive_path_invalid"))?;
    fs::create_dir_all(parent).map_err(|error| {
        ExtractError::FailedDetail(format!(
            "native_archive_file_parent_failure:{}:{}",
            relative_path.display(),
            error.kind()
        ))
    })?;
    ensure_canonical_descendant(staging, parent)?;
    let mut target = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&output)
        .map_err(|error| {
            ExtractError::FailedDetail(format!(
                "native_archive_file_failure:{}:{}",
                relative_path.display(),
                error.kind()
            ))
        })?;
    let mut buffer = vec![0_u8; BUFFER_SIZE];
    let mut file_bytes = 0_u64;
    loop {
        progress.poll(observer, entries_extracted, *total_bytes, false)?;
        let count = entry
            .read(&mut buffer)
            .map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
        if count == 0 {
            break;
        }
        file_bytes = file_bytes
            .checked_add(count as u64)
            .ok_or(ExtractError::Failed("native_archive_file_size_limit"))?;
        *total_bytes = total_bytes
            .checked_add(count as u64)
            .ok_or(ExtractError::Failed("native_archive_total_size_limit"))?;
        if file_bytes > request.maximum_file_bytes {
            return Err(ExtractError::Failed("native_archive_file_size_limit"));
        }
        if *total_bytes > request.maximum_total_bytes {
            return Err(ExtractError::Failed("native_archive_total_size_limit"));
        }
        let expansion_limit = request
            .maximum_expansion_ratio
            .checked_mul(source_bytes)
            .unwrap_or(u64::MAX);
        if *total_bytes > expansion_limit {
            return Err(ExtractError::Failed("native_archive_expansion_ratio_limit"));
        }
        target
            .write_all(&buffer[..count])
            .map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
    }
    target
        .flush()
        .map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
    if file_bytes != declared_size {
        return Err(ExtractError::Failed("native_archive_entry_size_mismatch"));
    }
    let mode = entry.header().mode().unwrap_or(0o644) & 0o777;
    apply_file_mode(&output, mode)
}

fn materialize_deferred_tar_hard_links(
    staging: &Path,
    mut pending: Vec<DeferredTarHardLink>,
) -> ExtractResult<()> {
    while !pending.is_empty() {
        let pending_count = pending.len();
        let mut unresolved = Vec::new();
        for link in pending {
            if !materialize_tar_hard_link(staging, &link.destination, &link.target, link.mode)? {
                unresolved.push(link);
            }
        }
        if unresolved.len() == pending_count {
            let preview = unresolved
                .iter()
                .take(5)
                .map(|link| format!("{}->{}", link.destination.display(), link.target.display()))
                .collect::<Vec<_>>()
                .join(",");
            return Err(ExtractError::FailedDetail(format!(
                "native_archive_hard_link_unresolved:{preview}"
            )));
        }
        pending = unresolved;
    }
    Ok(())
}

fn materialize_tar_special_entry(
    staging: &Path,
    destination: &Path,
    mode: u32,
) -> ExtractResult<()> {
    let output = staging.join(destination);
    let parent = output
        .parent()
        .ok_or(ExtractError::Failed("native_archive_path_invalid"))?;
    fs::create_dir_all(parent).map_err(|error| {
        ExtractError::FailedDetail(format!(
            "native_archive_special_entry_parent_failure:{}:{}",
            destination.display(),
            error.kind()
        ))
    })?;
    ensure_canonical_descendant(staging, parent)?;
    OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&output)
        .map_err(|error| {
            ExtractError::FailedDetail(format!(
                "native_archive_special_entry_failure:{}:{}",
                destination.display(),
                error.kind()
            ))
        })?;
    apply_file_mode(&output, mode)
}

fn materialize_tar_hard_link(
    staging: &Path,
    destination: &Path,
    target: &Path,
    mode: u32,
) -> ExtractResult<bool> {
    let source = staging.join(target);
    let source_metadata = match fs::symlink_metadata(&source) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(false),
        Err(error) => {
            return Err(ExtractError::FailedDetail(format!(
                "native_archive_hard_link_target_failure:{}:{}",
                target.display(),
                error.kind()
            )));
        }
    };
    if !source_metadata.file_type().is_file() {
        return Err(ExtractError::Failed("native_archive_link_target_invalid"));
    }

    let output = staging.join(destination);
    let parent = output
        .parent()
        .ok_or(ExtractError::Failed("native_archive_path_invalid"))?;
    fs::create_dir_all(parent).map_err(|error| {
        ExtractError::FailedDetail(format!(
            "native_archive_hard_link_parent_failure:{}:{}",
            destination.display(),
            error.kind()
        ))
    })?;
    ensure_canonical_descendant(staging, parent)?;
    ensure_canonical_descendant(staging, &source)?;

    fs::copy(&source, &output).map_err(|error| {
        ExtractError::FailedDetail(format!(
            "native_archive_hard_link_copy_failure:{}:{}",
            destination.display(),
            error.kind()
        ))
    })?;
    apply_file_mode(&output, mode)?;
    Ok(true)
}

fn ensure_canonical_descendant(staging: &Path, path: &Path) -> ExtractResult<()> {
    let canonical_staging = fs::canonicalize(staging)
        .map_err(|_| ExtractError::Failed("native_archive_staging_invalid"))?;
    let canonical_path =
        fs::canonicalize(path).map_err(|_| ExtractError::Failed("native_archive_path_invalid"))?;
    if !canonical_path.starts_with(canonical_staging) {
        return Err(ExtractError::Failed("native_archive_path_invalid"));
    }
    Ok(())
}

#[cfg(unix)]
fn apply_file_mode(path: &Path, mode: u32) -> ExtractResult<()> {
    fs::set_permissions(path, fs::Permissions::from_mode(mode))
        .map_err(|_| ExtractError::Failed("native_archive_io_failure"))
}

#[cfg(not(unix))]
fn apply_file_mode(_path: &Path, _mode: u32) -> ExtractResult<()> {
    Ok(())
}

#[cfg(unix)]
fn apply_directory_mode(path: &Path, mode: u32) -> ExtractResult<()> {
    fs::set_permissions(path, fs::Permissions::from_mode(mode))
        .map_err(|_| ExtractError::Failed("native_archive_io_failure"))
}

#[cfg(not(unix))]
fn apply_directory_mode(_path: &Path, _mode: u32) -> ExtractResult<()> {
    Ok(())
}

fn validate_paths(
    source: &Path,
    destination: &Path,
    staging: &Path,
    maximum_archive_bytes: u64,
) -> ExtractResult<u64> {
    let metadata = fs::symlink_metadata(source)
        .map_err(|_| ExtractError::Failed("native_archive_source_missing"))?;
    if metadata.file_type().is_symlink() {
        return Err(ExtractError::Failed("native_archive_source_symlink"));
    }
    if !metadata.is_file() {
        return Err(ExtractError::Failed("native_archive_source_not_regular"));
    }
    if metadata.len() > maximum_archive_bytes {
        return Err(ExtractError::Failed("native_archive_source_size_limit"));
    }
    if destination.exists() {
        return Err(ExtractError::Failed("native_archive_destination_exists"));
    }
    let destination_parent = destination
        .parent()
        .ok_or(ExtractError::Failed("native_archive_destination_invalid"))?;
    let staging_parent = staging
        .parent()
        .ok_or(ExtractError::Failed("native_archive_destination_invalid"))?;
    if destination_parent != staging_parent || destination == staging {
        return Err(ExtractError::Failed("native_archive_destination_invalid"));
    }
    Ok(metadata.len())
}

fn verify_artifact(
    source: &Path,
    source_bytes: u64,
    expected_bytes: Option<u64>,
    expected_sha256: Option<&str>,
    progress: &mut ProgressGate,
    observer: &mut ProgressObserver<'_>,
) -> ExtractResult<()> {
    if let Some(expected) = expected_bytes
        && source_bytes != expected
    {
        return Err(ExtractError::Failed("native_archive_size_mismatch"));
    }
    let Some(expected) = expected_sha256 else {
        return Ok(());
    };
    if expected.len() != 64 || !expected.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return Err(ExtractError::Failed("native_archive_digest_invalid"));
    }
    let mut file =
        File::open(source).map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
    let mut digest = Sha256::new();
    let mut buffer = vec![0_u8; BUFFER_SIZE];
    loop {
        progress.poll(observer, 0, 0, false)?;
        let count = file
            .read(&mut buffer)
            .map_err(|_| ExtractError::Failed("native_archive_io_failure"))?;
        if count == 0 {
            break;
        }
        digest.update(&buffer[..count]);
    }
    let actual = digest
        .finalize()
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect::<String>();
    if !actual.eq_ignore_ascii_case(expected) {
        return Err(ExtractError::Failed("native_archive_digest_mismatch"));
    }
    Ok(())
}

fn preflight(
    archive: &mut ZipArchive<File>,
    request: &ExtractRequest,
    progress: &mut ProgressGate,
    observer: &mut ProgressObserver<'_>,
) -> ExtractResult<Vec<SafeEntry>> {
    let mut safe_entries = Vec::with_capacity(archive.len());
    let mut seen = HashSet::with_capacity(archive.len());
    let mut declared_total = 0_u64;
    for index in 0..archive.len() {
        progress.poll(observer, safe_entries.len(), 0, false)?;
        if safe_entries.len() >= request.maximum_entries {
            return Err(ExtractError::Failed("native_archive_entry_limit"));
        }
        let entry = archive
            .by_index(index)
            .map_err(|_| ExtractError::Failed("native_archive_entry_unreadable"))?;
        let relative_path = safe_relative_path(entry.name(), request.maximum_depth)?;
        let key = relative_path.to_string_lossy().into_owned();
        if !seen.insert(key) {
            return Err(ExtractError::Failed("native_archive_duplicate_entry"));
        }
        let directory = entry.is_dir();
        if !directory && !is_regular_file(entry.unix_mode()) {
            return Err(ExtractError::Failed("native_archive_special_entry"));
        }
        let declared_size = entry.size();
        if declared_size > request.maximum_file_bytes {
            return Err(ExtractError::Failed("native_archive_file_size_limit"));
        }
        declared_total = declared_total
            .checked_add(declared_size)
            .ok_or(ExtractError::Failed("native_archive_total_size_limit"))?;
        if declared_total > request.maximum_total_bytes {
            return Err(ExtractError::Failed("native_archive_total_size_limit"));
        }
        safe_entries.push(SafeEntry {
            name: entry.name().to_owned(),
            relative_path,
            directory,
            declared_size,
        });
    }
    Ok(safe_entries)
}

fn safe_relative_path(raw: &str, maximum_depth: usize) -> ExtractResult<PathBuf> {
    let bytes = raw.as_bytes();
    if raw.is_empty()
        || bytes.contains(&0)
        || raw.contains('\\')
        || raw.starts_with('/')
        || has_drive_prefix(raw)
    {
        return Err(ExtractError::Failed("native_archive_path_invalid"));
    }
    let normalized = raw.strip_suffix('/').unwrap_or(raw);
    if normalized.is_empty() || normalized.as_bytes().len() > MAXIMUM_PATH_BYTES {
        return Err(ExtractError::Failed(
            if normalized.as_bytes().len() > MAXIMUM_PATH_BYTES {
                "native_archive_path_length_limit"
            } else {
                "native_archive_path_invalid"
            },
        ));
    }
    let segments: Vec<&str> = normalized.split('/').collect();
    if segments.len() > maximum_depth {
        return Err(ExtractError::Failed("native_archive_depth_limit"));
    }
    if segments
        .iter()
        .any(|segment| segment.is_empty() || *segment == "." || *segment == "..")
    {
        return Err(ExtractError::Failed("native_archive_path_invalid"));
    }
    Ok(segments.iter().collect())
}

fn safe_tar_relative_path(raw: &str, maximum_depth: usize) -> ExtractResult<PathBuf> {
    let mut normalized = raw;
    while let Some(stripped) = normalized.strip_prefix("./") {
        normalized = stripped;
    }
    normalized = normalized.strip_suffix('/').unwrap_or(normalized);
    if normalized.is_empty() {
        return Ok(PathBuf::new());
    }
    safe_relative_path(normalized, maximum_depth)
}

fn has_drive_prefix(value: &str) -> bool {
    let bytes = value.as_bytes();
    bytes.len() >= 2 && bytes[0].is_ascii_alphabetic() && bytes[1] == b':'
}

fn is_regular_file(unix_mode: Option<u32>) -> bool {
    unix_mode.is_none_or(|mode| {
        let file_type = mode & UNIX_TYPE_MASK;
        file_type == 0 || file_type == UNIX_REGULAR_FILE
    })
}

fn remove_owned_tree(path: &Path) -> std::io::Result<()> {
    match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.is_dir() && !metadata.file_type().is_symlink() => {
            fs::remove_dir_all(path)
        }
        Ok(_) => fs::remove_file(path),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(error),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

    #[test]
    fn rejects_paths_that_can_escape_or_change_platform_semantics() {
        for path in ["../escape", "/absolute", "C:/drive", "a\\b", "a//b", "./a"] {
            assert!(safe_relative_path(path, 8).is_err(), "accepted {path}");
        }
        assert_eq!(
            safe_relative_path("group/file.bin", 8).unwrap(),
            PathBuf::from("group/file.bin")
        );
    }

    #[test]
    fn rejects_symlink_mode_but_accepts_regular_or_unspecified_mode() {
        assert!(is_regular_file(None));
        assert!(is_regular_file(Some(0o100644)));
        assert!(!is_regular_file(Some(0o120777)));
    }

    #[test]
    fn accepts_rootfs_dot_prefix_but_rejects_tar_parent_escape() {
        assert_eq!(safe_tar_relative_path("./", 8).unwrap(), PathBuf::new());
        assert_eq!(
            safe_tar_relative_path("./usr/bin/env", 8).unwrap(),
            PathBuf::from("usr/bin/env")
        );
        assert!(safe_tar_relative_path("./../escape", 8).is_err());
        assert!(safe_tar_relative_path("/absolute", 8).is_err());
    }

    #[test]
    fn zip_transaction_supports_live_cancel_cleanup_and_immutable_reuse() {
        let root = temporary_root("zip-transaction");
        fs::create_dir_all(&root).unwrap();
        let source = root.join("source.zip");
        let destination = root.join("published");
        let staging = root.join(".published.part");
        write_zip(&source, "payload.bin", &vec![7_u8; 3 * 1024 * 1024]);
        let request = zip_request(&source, &destination, &staging, Some("v1:test"));
        let mut callbacks = 0;
        let cancelled = execute(&request, &mut |_, bytes| {
            callbacks += 1;
            Ok(bytes < PROGRESS_BYTES)
        });
        assert_eq!(cancelled.status, "cancelled");
        assert!(callbacks >= 2);
        assert!(!destination.exists());
        assert!(!staging.exists());

        let completed = execute(&request, &mut |_, _| Ok(true));
        assert_eq!(completed.status, "success");
        assert!(destination.join("payload.bin").is_file());
        assert!(destination.join(".kite-archive-ready").is_file());
        let mut reuse_callbacks = 0;
        let reused = execute(&request, &mut |_, _| {
            reuse_callbacks += 1;
            Ok(true)
        });
        assert_eq!(reused.status, "success");
        assert_eq!(reuse_callbacks, 0);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn zip_transaction_rejects_escape_before_publication() {
        let root = temporary_root("zip-escape");
        fs::create_dir_all(&root).unwrap();
        let source = root.join("source.zip");
        let destination = root.join("published");
        let staging = root.join(".published.part");
        write_zip(&source, "../escape.txt", b"escape");
        let response = execute(
            &zip_request(&source, &destination, &staging, None),
            &mut |_, _| Ok(true),
        );
        assert_eq!(response.status, "failure");
        assert_eq!(
            response.reason.as_deref(),
            Some("native_archive_path_invalid")
        );
        assert!(!root.join("escape.txt").exists());
        assert!(!destination.exists());
        assert!(!staging.exists());
        fs::remove_dir_all(root).unwrap();
    }

    fn temporary_root(label: &str) -> PathBuf {
        let nonce = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        std::env::temp_dir().join(format!(
            "kite-archive-{label}-{}-{nonce}",
            std::process::id()
        ))
    }

    fn write_zip(path: &Path, name: &str, payload: &[u8]) {
        let file = File::create(path).unwrap();
        let mut archive = ZipWriter::new(file);
        archive
            .start_file(name, SimpleFileOptions::default())
            .unwrap();
        archive.write_all(payload).unwrap();
        archive.finish().unwrap();
    }

    fn zip_request(
        source: &Path,
        destination: &Path,
        staging: &Path,
        reuse_key: Option<&str>,
    ) -> ExtractRequest {
        ExtractRequest {
            source: source.to_string_lossy().into_owned(),
            destination: destination.to_string_lossy().into_owned(),
            staging_directory: staging.to_string_lossy().into_owned(),
            maximum_archive_bytes: 8 * 1024 * 1024,
            maximum_entries: 100,
            maximum_total_bytes: 8 * 1024 * 1024,
            maximum_file_bytes: 8 * 1024 * 1024,
            maximum_depth: 8,
            maximum_expansion_ratio: 1_000,
            reuse_key: reuse_key.map(str::to_owned),
        }
    }
}
