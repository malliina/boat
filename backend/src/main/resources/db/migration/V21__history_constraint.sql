alter table push_history
  drop constraint push_history_ibfk_1;
alter table push_history
  add constraint push_history_ibfk_1 foreign key (client) references push_clients (id) on update cascade on delete set null;
