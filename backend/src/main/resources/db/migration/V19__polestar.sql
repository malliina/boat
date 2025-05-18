alter table refresh_tokens
  add column service varchar(128) not null default 'siwa';
