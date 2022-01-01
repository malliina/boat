create table refresh_tokens
(
    id                varchar(128)                              not null primary key,
    refresh_token     varchar(2048)                             not null,
    owner             bigint                                    not null references users (id) on update cascade on delete cascade,
    last_verification timestamp(3) default current_timestamp(3) not null,
    added             timestamp(3) default current_timestamp(3) not null
);
