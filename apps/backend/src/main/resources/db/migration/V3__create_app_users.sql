create table app_users (
    id varchar(80) not null primary key,
    display_name varchar(120) not null,
    password_hash varchar(255) not null,
    role_code varchar(40) not null,
    organization varchar(160) not null,
    status varchar(40) not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);
