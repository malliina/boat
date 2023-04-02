create table users
(
    id       bigint auto_increment primary key,
    user     varchar(128)                              not null,
    enabled  tinyint(1)                                not null,
    added    timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    email    varchar(128)                              null,
    token    varchar(128)                              null,
    language varchar(64)  default 'en'                 null,
    constraint email unique (email),
    constraint token unique (token),
    constraint user unique (user)
) charset = utf8mb4;

create index users_email_idx on users (email);

create table boats
(
    id    bigint auto_increment primary key,
    name  varchar(128)                              not null,
    token varchar(128)                              not null,
    owner bigint                                    not null,
    added timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    constraint name unique (name),
    constraint token unique (token),
    constraint boats_owner_fk foreign key (owner) references users (id) on update cascade on delete cascade
) charset = utf8mb4;

create table tracks
(
    id             bigint auto_increment primary key,
    name           varchar(128)                              not null,
    boat           bigint                                    not null,
    added          timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    avg_speed      double                                    null,
    avg_water_temp double                                    null,
    distance       double       default 0                    null,
    points         int          default 0                    null,
    title          varchar(191)                              null,
    canonical      varchar(191)                              not null,
    comments       text                                      null,
    constraint canonical unique (canonical),
    constraint name unique (name),
    constraint title unique (title),
    constraint tracks_boat_fk foreign key (boat) references boats (id) on update cascade on delete cascade
) charset = utf8mb4;

create table points
(
    id            bigint auto_increment primary key,
    longitude     double                                    not null,
    latitude      double                                    not null,
    boat_speed    double                                    not null,
    water_temp    double                                    not null,
    depth         double                                    null,
    depth_offset  double                                    null,
    boat_time     timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    track         bigint                                    not null,
    added         timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    coord         point                                     not null,
    track_index   int          default 0                    not null,
    depthm        double       default 0                    null,
    depth_offsetm double       default 0                    null,
    diff          double       default 0                    null,
    constraint points_track_track_index_idx unique (track, track_index),
    constraint points2_track_fk foreign key (track) references tracks (id) on update cascade on delete cascade
) charset = utf8mb4;

create index points_added_idx on points (added);
create index points_all_idx on points (track, boat_time, boat_speed, water_temp, diff);
create index points_boat_speed_idx on points (boat_speed);
create index points_boat_time_idx on points (boat_time);
create index points_coord_idx on points (coord);
create index points_depth_offsetm_idx on points (depth_offsetm);
create index points_depthm_idx on points (depthm);
create index points_track_boat_speed_idx on points (track, boat_speed);
create index points_track_boat_speed_time_idx on points (track, boat_time, boat_speed);
create index points_track_boat_time_idx on points (track, boat_time);
create index points_track_depth_idx on points (track, depthm);
create index points_track_diff_idx on points (track, diff);
create index points_track_index_idx on points (track_index);
create index points_track_water_temp_idx on points (track, water_temp);
create index points_water_temp_idx on points (water_temp);
create index track_demo_idx on points (track, boat_time, boat_speed, diff);

create table sentences
(
    id       bigint auto_increment primary key,
    sentence text                                      not null,
    track    bigint                                    not null,
    added    timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    constraint sentences_track_fk foreign key (track) references tracks (id) on update cascade on delete cascade
) charset = utf8mb4;

create table sentence_points
(
    sentence bigint not null,
    point    bigint not null,
    primary key (sentence, point),
    constraint sentence_points_point_fk foreign key (point) references points (id) on update cascade on delete cascade,
    constraint sentence_points_sentence_fk foreign key (sentence) references sentences (id) on update cascade on delete cascade
) charset = utf8mb4;

create table push_clients
(
    id     bigint auto_increment primary key,
    token  varchar(191) unique                      not null,
    device varchar(128)                              not null,
    user   bigint                                    not null,
    added  timestamp(3) default CURRENT_TIMESTAMP(3) not null
) charset = utf8mb4;

create table gps_points
(
    id          bigint auto_increment primary key,
    longitude   double                                    not null,
    latitude    double                                    not null,
    coord       geometry                                  not null,
    satellites  int                                       not null,
    fix         text                                      not null,
    point_index int          default 0                    not null,
    gps_time    timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    diff        double       default 0                    not null,
    device      bigint                                    not null,
    added       timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    constraint gps_points_device_fk foreign key (device) references boats (id) on update cascade on delete cascade
);

create index gps_points_device_gps_time_idx on gps_points (device, gps_time);
create index gps_points_point_index_idx on gps_points (point_index);

create table gps_sentences
(
    id       bigint auto_increment primary key,
    sentence varchar(128)                              not null,
    device   bigint                                    not null,
    added    timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    constraint gps_sentences_boat_fk foreign key (device) references boats (id) on update cascade on delete cascade
) charset = utf8mb4;

create table gps_sentence_points
(
    sentence bigint not null,
    point    bigint not null,
    primary key (sentence, point),
    constraint gps_sentence_points_point_fk foreign key (point) references gps_points (id) on update cascade on delete cascade,
    constraint gps_sentence_points_sentence_fk foreign key (sentence) references gps_sentences (id) on update cascade on delete cascade
);

create table fairways
(
    id         bigint auto_increment primary key,
    name_fi    varchar(128) null,
    name_se    varchar(128) null,
    start      varchar(128) null,
    end        varchar(128) null,
    depth      double       null,
    depth2     double       null,
    depth3     double       null,
    lighting   int          not null,
    class_text text         not null,
    sea_area   int          not null,
    state      double       not null
) charset = utf8mb4;

create table fairway_coords
(
    id         bigint auto_increment primary key,
    coord      geometry     not null,
    latitude   double       not null,
    longitude  double       not null,
    coord_hash varchar(191) not null,
    fairway    bigint       not null,
    constraint fairway_coords_fairway_fk foreign key (fairway) references fairways (id) on update cascade on delete cascade
) charset = utf8mb4;

create index fairway_coords_coord_hash_idx on fairway_coords (coord_hash);
