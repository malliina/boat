create table refresh_tokens
(
    id              bigint auto_increment primary key,
    refresh_token   varchar(2048)                             not null,
    owner           bigint                                    not null references users (id),
    last_validation timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    added           timestamp(3) default CURRENT_TIMESTAMP(3) not null
);
