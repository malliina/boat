alter table push_clients
  add column device_id varchar(191) null,
  add column live_activity varchar(191) null,
  modify column token varchar(1024) not null;
