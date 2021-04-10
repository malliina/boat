create table users_boats
(
    user  bigint                                    not null,
    boat  bigint                                    not null,
    state varchar(128)                              not null,
    added timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    foreign key (user) references users (id) on update cascade on delete cascade,
    foreign key (boat) references boats (id) on update cascade on delete cascade,
    primary key (user, boat)
) charset = utf8mb4;
