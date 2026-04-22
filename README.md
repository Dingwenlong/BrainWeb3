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

## 一键启动
如果你想一次性拉起基础设施、前端、后端和 EEG 服务，可以直接在仓库根目录执行：

```powershell
.\scripts\bootstrap\start-all.ps1
```

首轮启动如果本地还没装依赖，推荐：

```powershell
.\scripts\bootstrap\start-all.ps1 -InstallDeps
```

如果还想一并打开联邦服务占位进程：

```powershell
.\scripts\bootstrap\start-all.ps1 -InstallDeps -IncludeFederatedService
```

也可以用 npm 入口：

```powershell
npm run dev:all
```

## 新电脑一键启动
如果是第一次在一台新的 Windows 电脑上启动这个项目，推荐直接双击根目录的 `start-project.cmd`，或手动执行：

```powershell
.\start-project.cmd
```

它会按顺序完成这些事情：

- 自动安装或补齐 `Node.js LTS`、`Java 17`、`Python 3.11`、`Docker Desktop`
- 在仓库内下载一份可复用的便携版 `Maven`
- 自动启动 Docker Desktop 并等待基础设施可用
- 自动执行依赖安装，然后拉起前端、后端和 EEG 服务

如需同时启动联邦服务占位进程：

```powershell
.\start-project.cmd -IncludeFederatedService
```

如果你更习惯用 npm，也可以执行：

```powershell
npm run dev:new-machine
```

对应的一键停止命令：

```powershell
.\scripts\bootstrap\stop-all.ps1
```

如果只想关前端、后端和 EEG 服务窗口，保留 Docker 基础设施：

```powershell
.\scripts\bootstrap\stop-all.ps1 -KeepInfrastructure
```

也可以用 npm：

```powershell
npm run dev:stop
```

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
- 当前仓库已具备统一回归入口：`npm run verify:ci`、`npm run test:backend`、`npm run test:python-services`

## P5 验证基线
- 前端与合约统一回归：`npm run verify:ci`
- 后端回归：`npm run test:backend`
- Python 服务回归：`npm run test:python-services`
- 正式环境预检：`npm run check:prod-env`
- 联调账号种子 SQL：`npm run seed:accounts -- -Profile standard-demo-roles -DefaultPassword <联调密码>`
- 联调账号 SQL 导入：`npm run import:account-seed -- -EnvFile .\.env.production -SqlPath .\artifacts\account-seeds\<生成的 SQL 文件>`
- 正式环境 smoke：`npm run smoke:backend -- -BaseUrl http://backend.internal:8080 -ActorId <账号> -Password <密码>`
- 联调/验收报告：`npm run report:acceptance -- -EnvFile .\.env.production -BaseUrl http://backend.internal:8080 -ActorId <账号> -Password <密码>`
- 交付包整理：`npm run package:delivery -- -EnvironmentLabel staging`

`GitHub Actions` 当前会在 `push / pull_request` 上执行上述三组回归，作为 `P5` 阶段的交付底线。更完整的环境联调、验收与安全收口说明见 [docs/P5 交付与验收基线.md](</D:/Devs/BrainWeb3/docs/P5 交付与验收基线.md>) 和 [docs/P5 正式环境联调清单.md](</D:/Devs/BrainWeb3/docs/P5 正式环境联调清单.md>).

如果需要把本轮验收结果、演示脚本和账号/边界说明整理成一个可交付目录，可以执行：

```powershell
npm run package:delivery -- -EnvironmentLabel simulated-prod
```

该命令会优先收集 `artifacts/acceptance/` 下最近一份验收报告，并把联调清单、演示脚本、账号清单、验收路径、边界说明和环境模板一起整理到 `artifacts/delivery/`。
