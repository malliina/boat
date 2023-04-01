create table car_points
(
  id          bigint auto_increment primary key,
  longitude   double                                    not null,
  latitude    double                                    not null,
  coord       geometry                                  not null,
  gps_time    timestamp(3) default CURRENT_TIMESTAMP(3) not null,
  diff        double       default 0                    not null,
  device      bigint                                    not null,
  added       timestamp(3) default CURRENT_TIMESTAMP(3) not null,
  constraint car_points_device_fk foreign key (device) references boats (id) on update cascade on delete cascade
);
