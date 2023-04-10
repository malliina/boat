create table push_history
(
  id     bigint auto_increment primary key,
  device bigint                                    not null,
  added  timestamp(3) default CURRENT_TIMESTAMP(3) not null,
  constraint push_history_device_fk foreign key (device) references boats (id) on update cascade on delete cascade
) charset = utf8mb4;

create index push_history_device_idx on push_history(device, added);
