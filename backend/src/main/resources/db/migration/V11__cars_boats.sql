alter table points
  modify column water_temp double default null,
  rename column boat_speed to speed,
  rename column boat_time to source_time,
  add column altitude            double null,
  add column accuracy            double null,
  add column bearing             double null,
  add column bearing_accuracy    double null,
  add column battery             double null,
  add column car_range           double null,
  add column outside_temperature double null,
  add column night_mode          bool   null;
