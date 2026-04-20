# Solidity Contracts

当前目录放置 BrainWeb3 的智能合约骨架，首批包括：

- `DataNotary.sol`
- `AccessControl.sol`
- `ContributionLedger.sol`
- `DestroyManager.sol`
- `DidRegistry.sol`

当前目录仍使用 `Hardhat` 做本地编译和测试，但真实链路目标已经收敛到 `FISCO BCOS 3.x`。

- `Hardhat`：仅负责本地合约开发辅助
- `Solidity`：收敛到 `0.8.11`
- 真实部署/调用：后续切到后端 `Java SDK + FISCO BCOS 3.x`

本阶段目标是先固定结构体、事件和基础写入能力，下一阶段再接入 `FISCO` 节点、部署脚本、权限模型和链上联调。
