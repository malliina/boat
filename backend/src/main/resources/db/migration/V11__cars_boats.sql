alter table points
  rename column boat_speed to speed,
  rename column boat_time to source_time,
  modify column water_temp double default null,
  add column altitude            double null,
  add column accuracy            double null,
  add column bearing             double null,
  add column bearing_accuracy    double null,
  add column battery             double null,
  add column car_range           double null,
  add column outside_temperature double null,
  add column night_mode          bool   null;
