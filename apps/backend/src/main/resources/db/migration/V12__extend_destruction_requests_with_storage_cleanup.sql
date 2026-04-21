alter table destruction_requests
    add column cleanup_status varchar(40) not null default 'not-requested';

alter table destruction_requests
    add column cleanup_error varchar(1000);

alter table destruction_requests
    add column cleanup_completed_at timestamp(6);
