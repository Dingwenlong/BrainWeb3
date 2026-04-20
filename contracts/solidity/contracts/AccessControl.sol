// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

contract AccessControl {
    struct AccessGrant {
        string datasetId;
        string subjectDid;
        string purpose;
        uint64 expiresAt;
        bool active;
    }

    mapping(bytes32 => AccessGrant) private grants;

    event AccessGranted(
        bytes32 indexed grantId,
        string datasetId,
        string subjectDid,
        string purpose,
        uint64 expiresAt
    );

    event AccessRevoked(bytes32 indexed grantId, string datasetId, string subjectDid);

    function grantAccess(
        string calldata datasetId,
        string calldata subjectDid,
        string calldata purpose,
        uint64 expiresAt
    ) external returns (bytes32) {
        bytes32 grantId = keccak256(abi.encode(datasetId, subjectDid, purpose, expiresAt));
        grants[grantId] = AccessGrant({
            datasetId: datasetId,
            subjectDid: subjectDid,
            purpose: purpose,
            expiresAt: expiresAt,
            active: true
        });

        emit AccessGranted(grantId, datasetId, subjectDid, purpose, expiresAt);
        return grantId;
    }

    function revokeAccess(bytes32 grantId) external {
        AccessGrant storage grant = grants[grantId];
        require(grant.active, "grant inactive");
        grant.active = false;
        emit AccessRevoked(grantId, grant.datasetId, grant.subjectDid);
    }

    function getGrant(bytes32 grantId) external view returns (AccessGrant memory) {
        return grants[grantId];
    }
}
