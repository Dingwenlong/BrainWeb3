create table identity_credential_states (
    id bigint not null auto_increment primary key,
    subject_type varchar(40) not null,
    subject_key varchar(160) not null,
    credential_status varchar(40) not null,
    reason varchar(255),
    updated_by varchar(80) not null,
    updated_at timestamp(6) not null
);

create unique index uq_identity_credential_subject
    on identity_credential_states (subject_type, subject_key);
