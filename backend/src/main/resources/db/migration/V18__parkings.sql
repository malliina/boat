create table parking_areas
(
  id    varchar(128)                              not null primary key,
  added timestamp(3) default CURRENT_TIMESTAMP(3) not null
) charset = utf8mb4;

create table parking_coordinates
(
  area      varchar(128)                              not null,
  longitude double                                    not null,
  latitude  double                                    not null,
  coord     point                                     not null,
  added     timestamp(3) default CURRENT_TIMESTAMP(3) not null,
  constraint parking_coordinates_area_fk foreign key (area) references parking_areas (id) on update cascade on delete cascade
) charset = utf8mb4;
