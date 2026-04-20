# FISCO 本地联调手册

## 1. 目的
- 本手册用于在当前仓库内快速拉起一套本地 `FISCO BCOS 3.x` Docker 开发链，并为后端生成可直接使用的 `SDK` 证书与 `config.toml`。
- 当前脚本默认采用官方 `build_chain.sh` 的 `docker` 模式，版本默认对齐 `v3.6.0`。

## 2. 前置条件
- Docker 可正常运行
- PowerShell 可执行仓库脚本
- 本机可调用 `bash`

官方参考：
- [FISCO BCOS 3.x 使用 docker 部署区块链](https://fisco-bcos-doc.readthedocs.io/zh-cn/latest/docs/tutorial/docker.html)
- [FISCO BCOS 3.x SDK 连接证书配置](https://fisco-bcos-doc.readthedocs.io/zh-cn/release-3.0.0/docs/develop/sdk/cert_config.html)

## 3. 启动本地链
在仓库根目录执行：

```powershell
.\scripts\bootstrap\fisco-dev-up.ps1
```

Windows + Git Bash 环境下，脚本默认会把 FISCO 开发链工作目录放到：

`C:\Users\jimmy\.brainweb3-fisco-dev`

这样可以避开 Docker Desktop 对 `D:` 盘目录共享的限制。当前 Windows 本地联调模式默认收敛为：

- `1` 个节点
- Docker 显式端口映射
- RPC `disable_ssl=true`

这样能稳定支持宿主机 Java SDK 真实写链。若你想改到别的位置，可先设置：

```powershell
$env:BRAINWEB3_FISCO_ROOT='C:\your-shared-path\.brainweb3-fisco-dev'
```

脚本会自动完成：
- 下载官方 `build_chain.sh`
- 生成 `127.0.0.1:1` 的本地 Air 版 docker 链
- 启动 1 个节点
- 拷贝 SDK 证书到 `C:\Users\jimmy\.brainweb3-fisco-dev\sdk\conf`
- 生成后端可用配置：
  - `C:\Users\jimmy\.brainweb3-fisco-dev\sdk\config.toml`
  - `C:\Users\jimmy\.brainweb3-fisco-dev\sdk\backend.env`

如需强制重建链：

```powershell
.\scripts\bootstrap\fisco-dev-up.ps1 -ForceRebuild
```

## 4. 启用后端真实写链
PowerShell 中设置：

```powershell
$env:CHAIN_ENABLED='true'
$env:CHAIN_CONFIG_PATH='C:\Users\jimmy\.brainweb3-fisco-dev\sdk\config.toml'
mvn -pl apps/backend spring-boot:run
```

说明：
- 若未设置 `CHAIN_CONTRACT_ADDRESS`，后端会自动部署 `DataNotary`
- 上传数据时会先走链下 `LocalStorageGateway`，再调用 `FiscoBcosChainGateway` 写链
- 当前 Windows 本地链已完成真实上传烟测，接口可返回真实 `chainTxHash` 和 `auditState=fisco-registered`

## 5. 停止本地链

```powershell
.\scripts\bootstrap\fisco-dev-down.ps1
```

## 6. 当前限制
- 官方 docker 建链文档标明该模式目前面向 Linux 环境；当前脚本是通过本机 `bash` 包装执行，适合开发联调，不建议直接当生产部署方案
- 当前默认是非国密链
- 当前链下存储仍是本地文件系统，后续可继续接 `MinIO / S3 / 私有 IPFS`
- 启动脚本现在会按当前节点数检查所需 RPC 端口；若本轮联调所需端口未拉起，会直接判定启动失败
- Windows 本地模式为了稳定性关闭了 RPC SSL；如果后续要回到多节点 TLS 联调，建议在 Linux / WSL 原生环境下处理
