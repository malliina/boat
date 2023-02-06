create table vessels
(
    id          bigint auto_increment primary key,
    mmsi        varchar(128)                              not null,
    name        varchar(191)                              not null,
    coord       point                                     not null,
    sog         double                                    not null,
    cog         double                                    not null,
    draft       double                                    not null,
    destination varchar(128),
    heading     int,
    eta         bigint                                    not null,
    vessel_time bigint                                    not null,
    added       timestamp(3) default CURRENT_TIMESTAMP(3) not null
) charset = utf8mb4;

create index vessels_mmsi_idx on vessels (mmsi);
create index vessels_name_idx on vessels (name);
create index vessels_added_idx on vessels (added);
