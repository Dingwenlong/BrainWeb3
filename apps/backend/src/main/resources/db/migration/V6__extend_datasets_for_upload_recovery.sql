alter table datasets add column proof_fingerprint varchar(128);
alter table datasets add column last_upload_trace_id varchar(80);
alter table datasets add column last_error_message varchar(1000);
