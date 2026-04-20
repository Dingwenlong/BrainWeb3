create table datasets (
    id varchar(40) not null primary key,
    subject_code varchar(80) not null,
    title varchar(200) not null,
    description varchar(1000) not null,
    original_filename varchar(255) not null,
    file_size_bytes bigint not null,
    owner_organization varchar(160) not null,
    format varchar(16) not null,
    upload_status varchar(40) not null,
    proof_status varchar(40) not null,
    training_readiness varchar(60) not null,
    channel_count integer not null,
    sample_count integer not null,
    duration_seconds double not null,
    sampling_rate integer not null,
    storage_provider varchar(40),
    storage_key varchar(255),
    storage_uri varchar(1000),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create table data_asset_proofs (
    dataset_id varchar(40) not null primary key,
    chain_provider varchar(40),
    chain_group varchar(40),
    contract_name varchar(120),
    contract_address varchar(255),
    sm3_hash varchar(255),
    ipfs_cid varchar(255),
    off_chain_reference varchar(1000),
    chain_tx_hash varchar(255),
    did_holder varchar(255),
    access_policy varchar(255),
    audit_state varchar(80),
    constraint fk_data_asset_proofs_dataset
        foreign key (dataset_id) references datasets (id)
);

create table dataset_tags (
    dataset_id varchar(40) not null,
    tag_order integer not null,
    tag_value varchar(80) not null,
    primary key (dataset_id, tag_order),
    constraint fk_dataset_tags_dataset
        foreign key (dataset_id) references datasets (id)
);

create table upload_audits (
    id bigint not null auto_increment primary key,
    dataset_id varchar(40),
    action varchar(80) not null,
    status varchar(40) not null,
    message varchar(1000),
    trace_id varchar(80) not null,
    created_at timestamp(6) not null,
    constraint fk_upload_audits_dataset
        foreign key (dataset_id) references datasets (id)
);
