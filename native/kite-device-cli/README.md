# kite-device

`kite-device` 是 Ubuntu/PRoot 内供 Agent 使用的 Kite Device Bridge 客户端。

- 它不直接调用 Shizuku，也不复制 Android 权限和生命周期状态。
- 所有设备操作都复用 `kf-host-self` / `kf-adb-bridge` 的同一执行与取消通道。
- `capabilities --json` 读取 APK 投影的统一能力目录，并用实时探测结果声明当前传输可达项。
- 传输可达不等于当前会话已授权；调用方必须继续遵守 Agent 会话权限和目录中的风险等级。
- 发布制品是 `aarch64-unknown-linux-musl` 静态二进制，避免依赖 Ubuntu rootfs 的 libc 版本。

本机验证：

```powershell
rustup target add aarch64-unknown-linux-musl
$env:CARGO_TARGET_AARCH64_UNKNOWN_LINUX_MUSL_LINKER = 'rust-lld'
cargo test --manifest-path native/kite-device-cli/Cargo.toml
cargo build --locked --release --target aarch64-unknown-linux-musl --manifest-path native/kite-device-cli/Cargo.toml
```
