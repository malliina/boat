create table mmsis
(
    mmsi        varchar(128)                              not null primary key,
    name        varchar(191)                              not null,
    draft       double                                    not null,
    added       timestamp(3) default CURRENT_TIMESTAMP(3) not null
) charset = utf8mb4;

create index mmsis_mmsi_idx on mmsis (mmsi);
create index mmsis_name_idx on mmsis (name);

create table mmsi_updates
(
    mmsi        varchar(128)                              not null,
    coord       point                                     not null,
    sog         double                                    not null,
    cog         double                                    not null,
    destination varchar(128),
    heading     int,
    eta         bigint                                    not null,
    vessel_time bigint                                    not null,
    added       timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    constraint mmsi_updates_mmsi_fk foreign key (mmsi) references mmsis (mmsi) on update cascade on delete cascade
) charset = utf8mb4;

create index mmsi_updates_added_idx on mmsi_updates (added);
