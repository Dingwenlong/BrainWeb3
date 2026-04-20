// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

contract ContributionLedger {
    struct ContributionRecord {
        string trainingJobId;
        string participantDid;
        uint32 round;
        uint256 score;
        uint64 recordedAt;
    }

    mapping(bytes32 => ContributionRecord) private records;

    event ContributionRecorded(
        bytes32 indexed recordId,
        string trainingJobId,
        string participantDid,
        uint32 round,
        uint256 score
    );

    function recordContribution(
        string calldata trainingJobId,
        string calldata participantDid,
        uint32 round,
        uint256 score
    ) external returns (bytes32) {
        bytes32 recordId = keccak256(abi.encode(trainingJobId, participantDid, round));
        records[recordId] = ContributionRecord({
            trainingJobId: trainingJobId,
            participantDid: participantDid,
            round: round,
            score: score,
            recordedAt: uint64(block.timestamp)
        });

        emit ContributionRecorded(recordId, trainingJobId, participantDid, round, score);
        return recordId;
    }

    function getContribution(bytes32 recordId) external view returns (ContributionRecord memory) {
        return records[recordId];
    }
}
