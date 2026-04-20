// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

contract DataNotary {
    struct DataAsset {
        string datasetId;
        string ownerDid;
        string sm3Hash;
        string ipfsCid;
        uint64 registeredAt;
    }

    mapping(string => DataAsset) private assets;

    event DataRegistered(
        string indexed datasetId,
        string ownerDid,
        string sm3Hash,
        string ipfsCid,
        uint64 registeredAt
    );

    function registerDataAsset(
        string calldata datasetId,
        string calldata ownerDid,
        string calldata sm3Hash,
        string calldata ipfsCid
    ) external {
        require(bytes(datasetId).length > 0, "datasetId required");
        require(bytes(assets[datasetId].datasetId).length == 0, "dataset exists");

        DataAsset memory asset = DataAsset({
            datasetId: datasetId,
            ownerDid: ownerDid,
            sm3Hash: sm3Hash,
            ipfsCid: ipfsCid,
            registeredAt: uint64(block.timestamp)
        });

        assets[datasetId] = asset;
        emit DataRegistered(datasetId, ownerDid, sm3Hash, ipfsCid, asset.registeredAt);
    }

    function getDataAsset(string calldata datasetId) external view returns (DataAsset memory) {
        return assets[datasetId];
    }
}
