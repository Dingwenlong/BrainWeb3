# BrainWeb3

脑域链医是一个围绕脑电数据安全上传、链上存证、DID 授权、联邦学习、审计追溯与可视化分析构建的全栈 monorepo。

当前仓库已完成第 1 周工程骨架的第一版落盘，包含：

- `apps/backend`：Spring Boot 业务编排骨架
- `apps/frontend`：Vue 3 + Vite 门户基座
- `services/eeg-service`：Flask EEG 处理与 `brain-activity` mock 接口
- `services/federated-service`：Flask 联邦训练编排占位服务
- `contracts/solidity`：面向 `FISCO BCOS 3.x` 收敛的合约工程骨架
- `packages/shared-api`：OpenAPI 草案与接口共享约定
- `docs`：开发约定、环境部署手册、接口与错误码规范
- `scripts/bootstrap`：本地环境检查与基础设施启停脚本

## 快速开始
1. 复制环境变量模板。
   `Copy-Item .env.example .env`
2. 启动本地基础设施。
   `.\scripts\bootstrap\dev-up.ps1`
3. 安装 Node 侧依赖。
   `npm install`
4. 启动前端。
   `npm run dev:frontend`
5. 启动后端。
   `mvn -pl apps/backend spring-boot:run`
6. 安装 EEG 服务依赖并启动。
   `python -m pip install -r .\services\eeg-service\requirements.txt`
   `python .\services\eeg-service\app.py`
7. 下载一个真实 PhysioNet 演示样本。
   `.\scripts\bootstrap\fetch-physionet-sample.ps1`

下载完成后，默认 `ds-101` 会优先读取 `.brainweb3-samples\physionet\S001\S001R04.edf`。
如果样本不存在，系统仍会回退到当前的 bootstrap 占位路径。

## 当前基线
- Java 目标版本：`17`
- Python 目标版本：`3.10+`
- 前端：`Vue 3 + Vite + TypeScript`
- 链路方向：`FISCO BCOS 3.x Java SDK`
- 合约：`Solidity 0.8.11 + Hardhat（本地编译/测试辅助）`
- 链下存储：`StorageGateway -> Local Storage / MinIO`
- 基础设施：`MySQL + Redis + IPFS + MinIO`

`Hardhat` 当前只保留本地编译与单测职责，真实链路会继续收敛到后端 `Java SDK + FISCO BCOS 3.x`。说明见 [docs/FISCO 3.x 收敛说明.md](docs/FISCO%203.x%20收敛说明.md)。

## 当前补充
- `brain-activity` 已支持真实 EEG 频段聚合、时间区间查询和上传后元数据解析
- 数据详情页已拆成 3D 热力图、时间轴控制和脑区指标面板
- 可通过 `fetch-physionet-sample.ps1` 把 `ds-101` 切到真实 PhysioNet EDF 演示样本

## 下一步
- 接入真实 `FISCO BCOS 3.x` 节点、证书与部署脚本
- 补齐统一认证、链交互与审计链路
- 把默认 seed 数据详情也同步为真实样本元数据，而不只是把脑区分析切到真实 EDF
