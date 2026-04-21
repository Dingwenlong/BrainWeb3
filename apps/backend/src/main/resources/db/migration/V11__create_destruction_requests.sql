alter table datasets
    add column destruction_status varchar(40) not null default 'active';

alter table datasets
    add column destroyed_at timestamp(6);

alter table datasets
    add column destroyed_by varchar(80);

create table destruction_requests (
    id varchar(40) primary key,
    dataset_id varchar(40) not null,
    requester_id varchar(80) not null,
    requester_role varchar(40) not null,
    requester_org varchar(120) not null,
    reason varchar(1000) not null,
    status varchar(40) not null,
    policy_note varchar(255),
    approver_id varchar(80),
    approver_role varchar(40),
    approver_org varchar(120),
    executed_by varchar(80),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    decided_at timestamp(6),
    executed_at timestamp(6)
);

create index idx_destruction_requests_dataset_time
    on destruction_requests (dataset_id, created_at desc);

create index idx_destruction_requests_requester_time
    on destruction_requests (requester_id, created_at desc);
