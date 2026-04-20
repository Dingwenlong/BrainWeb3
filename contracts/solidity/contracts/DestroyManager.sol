// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

contract DestroyManager {
    struct DestroyProof {
        string datasetId;
        string operatorDid;
        string proofHash;
        uint64 destroyedAt;
    }

    mapping(string => DestroyProof) private proofs;

    event DataDestroyed(
        string indexed datasetId,
        string operatorDid,
        string proofHash,
        uint64 destroyedAt
    );

    function submitDestroyProof(
        string calldata datasetId,
        string calldata operatorDid,
        string calldata proofHash
    ) external {
        proofs[datasetId] = DestroyProof({
            datasetId: datasetId,
            operatorDid: operatorDid,
            proofHash: proofHash,
            destroyedAt: uint64(block.timestamp)
        });

        emit DataDestroyed(datasetId, operatorDid, proofHash, uint64(block.timestamp));
    }

    function getDestroyProof(string calldata datasetId) external view returns (DestroyProof memory) {
        return proofs[datasetId];
    }
}
