create table access_requests (
    id varchar(40) not null primary key,
    dataset_id varchar(40) not null,
    actor_id varchar(80) not null,
    actor_role varchar(40) not null,
    actor_org varchar(160) not null,
    purpose varchar(80) not null,
    requested_duration_hours integer not null,
    reason varchar(1000) not null,
    status varchar(40) not null,
    policy_note varchar(255),
    approved_duration_hours integer,
    approver_id varchar(80),
    approver_role varchar(40),
    approver_org varchar(160),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    decided_at timestamp(6),
    expires_at timestamp(6),
    constraint fk_access_requests_dataset
        foreign key (dataset_id) references datasets (id)
);

create table audit_events (
    id bigint not null auto_increment primary key,
    dataset_id varchar(40),
    actor_id varchar(80) not null,
    actor_role varchar(40) not null,
    actor_org varchar(160) not null,
    action varchar(80) not null,
    status varchar(40) not null,
    detail varchar(1000),
    created_at timestamp(6) not null,
    constraint fk_audit_events_dataset
        foreign key (dataset_id) references datasets (id)
);
