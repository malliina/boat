alter table car_points
  add column speed               double       null,
  add column battery             double       null,
  add column capacity            double       null,
  add column car_range           double       null,
  add column outside_temperature double       null,
  add column night_mode          bool         null,
  add column car_updated         timestamp(3) null;
