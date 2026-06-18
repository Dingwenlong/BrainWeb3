-- L3 domain-validation evidence: independent model evaluation runs with provenance hashes, anchored to chain.
create table if not exists evaluation_runs (
    id varchar(40) primary key,
    model_record_id varchar(40) not null,
    dataset_id varchar(40) not null,
    evaluator_actor_id varchar(80) not null,
    evaluator_role varchar(40) not null,
    evaluator_org varchar(160) not null,
    test_set_hash varchar(128) not null,
    eval_script_hash varchar(128) not null,
    metrics_json varchar(2000) not null,
    result_hash varchar(80) not null,
    verification_status varchar(40) not null,
    notes varchar(1000),
    created_at timestamp(6) not null
);
create index idx_evaluation_runs_model on evaluation_runs (model_record_id);

alter table model_records add column verification_status varchar(40) not null default 'unverified';
alter table model_records add column latest_evaluation_id varchar(40);
alter table model_records add column latest_result_hash varchar(80);
