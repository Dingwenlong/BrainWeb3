// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

contract DidRegistry {
    struct DidDocumentRef {
        string did;
        string documentUri;
        bool active;
        uint64 updatedAt;
    }

    mapping(string => DidDocumentRef) private didDocuments;

    event DidDocumentUpdated(
        string indexed did,
        string documentUri,
        bool active,
        uint64 updatedAt
    );

    function upsertDidDocument(
        string calldata did,
        string calldata documentUri,
        bool active
    ) external {
        DidDocumentRef memory documentRef = DidDocumentRef({
            did: did,
            documentUri: documentUri,
            active: active,
            updatedAt: uint64(block.timestamp)
        });

        didDocuments[did] = documentRef;
        emit DidDocumentUpdated(did, documentUri, active, documentRef.updatedAt);
    }

    function getDidDocument(string calldata did) external view returns (DidDocumentRef memory) {
        return didDocuments[did];
    }
}
