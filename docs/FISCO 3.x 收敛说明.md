# FISCO 3.x 收敛说明

## 1. 背景
- 项目设计目标中的联盟链底座是 `FISCO BCOS`，但首轮工程骨架为了先打通业务链路，临时采用了 `Hardhat` 作为本地合约编译和测试工具。
- 这套做法适合样板阶段，但不适合作为最终的联盟链接入方案，因为 `Hardhat` 并不是 `FISCO BCOS` 官方主路径，证书、SDK、节点配置和国密能力都需要回到 `FISCO BCOS 3.x` 官方工具链。

## 2. 本次收敛内容
- 后端引入 `org.fisco-bcos.java-sdk:fisco-bcos-java-sdk:3.6.0`，把真实链交互的主方向固定为 `Java SDK`。
- 后端新增 `brainweb3.chain.*` 配置入口，并提供示例配置文件：
  - `apps/backend/src/main/resources/fisco/config-example.toml`
  - `apps/backend/src/main/resources/fisco/clog.ini`
- 后端新增 `ChainGateway` 抽象，当前由 `MockChainGateway` 承接样板存证；后续无论接 `FISCO Java SDK` 还是扩展 `WeCross`，都不需要再把链逻辑塞回业务服务里。
- 后端已补 `FiscoBcosChainGateway`，可在 `CHAIN_ENABLED=true` 时切换到真实 `Java SDK -> DataNotary` 写链流程。
- 后端新增 `StorageGateway` 抽象，当前默认实现为 `LocalStorageGateway`，先把完整 EEG 文件稳定落到链下，再把链上登记和链下文件引用分开治理。
- 系统状态接口补充 `chain` 模块状态，用于明确当前处于 `fisco-bcos-3-bootstrap` 阶段。
- 合约工程的 Solidity 版本从 `0.8.28` 收敛到 `0.8.11`，以贴近 `FISCO BCOS 3.x` 官方 SDK/示例的兼容区间。
- `Hardhat` 继续保留，但职责限定为“本地编译和测试辅助”，不再作为真实链部署方案描述。

## 3. 当前状态
- 已完成：方向收敛、配置入口、示例配置、SDK 依赖、合约编译器版本对齐。
- 未完成：真实节点编排、SDK 证书注入、链上部署脚本、Java 侧合约调用封装、事件监听、SM 国密链路。

## 4. 已知注意点
- `FISCO BCOS 3.x` 官方 Java SDK 文档长期以 `JDK 11` 作为推荐环境；当前仓库后端基线仍是 `Java 17`。这版先保留 Java 17，后续以真实节点联调结果为准决定是否降级。
- Java SDK 依赖底层 `bcos-c-sdk`/证书材料时，开发机和部署环境都需要补齐动态库与证书目录，单有 Maven 依赖并不代表已经可连链。

## 5. 下一步建议
1. 在 `docker-compose` 之外补一套本地 `FISCO BCOS 3.x` 节点脚本或说明。
2. 先把 `DataNotary` 做成最小可部署合约，并生成后端调用所需封装。
3. 把当前上传样板链路中的 mock 存证摘要切到真实 `Java SDK -> FISCO BCOS` 写链流程。
