alter table points modify added timestamp(3) default CURRENT_TIMESTAMP(3) not null;
alter table boats modify added timestamp(3) default CURRENT_TIMESTAMP(3) not null;
alter table tracks modify added timestamp(3) default CURRENT_TIMESTAMP(3) not null;
alter table sentences modify added timestamp(3) default CURRENT_TIMESTAMP(3) not null;
alter table users modify added timestamp(3) default CURRENT_TIMESTAMP(3) not null;
alter table push_clients modify added timestamp(3) default CURRENT_TIMESTAMP(3) not null;
