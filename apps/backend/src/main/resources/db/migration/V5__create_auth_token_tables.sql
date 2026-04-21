create table auth_refresh_tokens (
  id varchar(64) primary key,
  user_id varchar(80) not null,
  token_hash varchar(64) not null unique,
  expires_at timestamp(6) not null,
  created_at timestamp(6) not null,
  last_used_at timestamp(6),
  revoked_at timestamp(6),
  replaced_by_token_id varchar(64),
  constraint fk_auth_refresh_tokens_user
    foreign key (user_id) references app_users(id)
);

create index idx_auth_refresh_tokens_user on auth_refresh_tokens(user_id);
create index idx_auth_refresh_tokens_expires_at on auth_refresh_tokens(expires_at);

create table auth_password_reset_tickets (
  id varchar(64) primary key,
  user_id varchar(80) not null,
  token_hash varchar(64) not null unique,
  created_at timestamp(6) not null,
  expires_at timestamp(6) not null,
  consumed_at timestamp(6),
  constraint fk_auth_password_reset_tickets_user
    foreign key (user_id) references app_users(id)
);

create index idx_auth_password_reset_tickets_user on auth_password_reset_tickets(user_id);
create index idx_auth_password_reset_tickets_expires_at on auth_password_reset_tickets(expires_at);
