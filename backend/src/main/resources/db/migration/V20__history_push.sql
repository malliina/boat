alter table push_history
  add column client bigint null references push_clients (id),
  add column live_activity varchar(191) null;
