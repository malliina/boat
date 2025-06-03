alter table push_clients
  add column active tinyint(1) not null default true;

alter table push_history
  add column outcome varchar(128) not null default 'unknown';
