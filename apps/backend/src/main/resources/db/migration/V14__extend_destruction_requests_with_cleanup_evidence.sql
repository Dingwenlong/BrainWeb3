alter table destruction_requests
    add column cleanup_evidence_ref varchar(255);

alter table destruction_requests
    add column cleanup_evidence_hash varchar(128);

alter table destruction_requests
    add column cleanup_verified_by varchar(80);
