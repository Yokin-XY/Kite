# Kite 远程资源目录

远程目录只发布资源卡、安装脚本和下载源策略，不承载大体积安装制品。实际制品仍由每张资源卡声明的官方源或国内传输源提供，并由摘要或完整 Git commit 校验。

## 发布结构

```text
store/v1/channels/stable.json
store/v1/channels/stable.sig
```

`stable.json` 是同一代首页布局和全部资源卡组成的完整快照，`stable.sig` 是对原始字节的 ECDSA P-256 签名。App 只有在验签、频道、代次、资源 id 和协议版本全部通过后，才会原子替换本地缓存；远端失败时继续使用上一次有效缓存或 APK 内置目录。

## 首次建立签名密钥

私钥不得提交。下面示例把它放进仓库已忽略的 `.autotask`：

```powershell
java tools/resource-store/ResourceStoreSigner.java generate .autotask/resource-store-private.key .autotask/resource-store-public.key
```

把公钥文件的 Base64 内容写入 `assets/resource-store/bootstrap.json` 的 `trustedKeys[].publicKey`。私钥只用于发布机签名。

## 构建和签名一个代次

每次内容变化必须递增 `revision`，同一代次不得发布不同内容。

```powershell
py -3 tools/resource-store/build_snapshot.py --resources assets/resources --output store/v1/channels/stable.json --revision 1 --key-id kite-store-2026-01
java tools/resource-store/ResourceStoreSigner.java sign .autotask/resource-store-private.key store/v1/channels/stable.json store/v1/channels/stable.sig kite-store-2026-01
```

GitCode 公共仓库建立后，把官方 raw API 的两个 HTTPS 地址写进 `bootstrap.json`：

```text
https://api.gitcode.com/api/v5/repos/<owner>/<repo>/raw/store/v1/channels/stable.json?ref=main
https://api.gitcode.com/api/v5/repos/<owner>/<repo>/raw/store/v1/channels/stable.sig?ref=main
```

客户端不保存 GitCode token；公开目录读取失败只会触发安全回退。
