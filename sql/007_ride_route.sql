-- Optional path geometry for owner-selected driving route (OSRM polyline as JSON [[lat,lng],...]).
ALTER TABLE rides ADD COLUMN IF NOT EXISTS route_geometry TEXT;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS route_distance_m DOUBLE PRECISION;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS route_duration_s DOUBLE PRECISION;
