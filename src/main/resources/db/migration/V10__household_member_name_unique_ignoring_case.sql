-- Members are looked up by name ignoring case, but UNIQUE (name) is case-sensitive: "anna" next to "Anna"
-- was accepted, and from then on every lookup of that name found two rows and failed with 500. The
-- uniqueness now ignores case as well. upper(), not lower(): Spring Data's ...IgnoreCase queries compare
-- UPPER(name) = UPPER(?), and the index has to apply the same rule (lower and upper disagree for some
-- letters) - it also serves those queries. A registry already holding a case-colliding pair fails here
-- loudly instead of losing a row; the cluster database was checked to hold none (one member).

ALTER TABLE household_members
    DROP CONSTRAINT household_members_name_uq;

CREATE UNIQUE INDEX household_members_name_upper_uq
    ON household_members (upper(name));
