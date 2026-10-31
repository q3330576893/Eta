# Fork CI 签名密钥（非官方）

- `eta-release.keystore.b64`：个人 fork 专用的一次性签名密钥（base64），仅用于 GitHub Actions 自动构建 release APK；CI 中解码为 `/tmp/eta-release.keystore` 使用。
- alias: `eta`，storepass/keypass: `eta-fork-2026`
- **不是** Eta 官方发布密钥，不要用于任何正式分发。
- 提交到仓库是为了让每次 CI 构建签名保持一致（换签名需卸载重装）。
