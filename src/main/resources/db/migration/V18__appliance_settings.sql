-- The settings of an appliance (HAS-272, smart-home-sdk 1.13.0): a washing machine, a dryer or a
-- dishwasher watched through the power draw of the Shelly plug it runs on. The device of the
-- registry is the plug, of the kind APPLIANCE; these columns are the ApplianceSettings of the SDK
-- model - how appliance-service reads a cycle from the draw and how often it reminds the household.
--
-- Eight columns, all filled for an appliance and all empty for any other kind (the model requires
-- every setting, and the registry stores all of them or none). The bounds are those of the SDK
-- schema; the quiet times are a local time of the house to the minute - the model carries them as
-- HH:mm text. That the plug is reached through a Shelly binding is DeviceRules', which says the
-- same in words before a write reaches these checks.
--
-- Nullable on purpose: every existing row and every row the released version inserts during the
-- rollout (its entity does not name the columns) is of another kind and leaves them empty.

ALTER TABLE devices
    ADD COLUMN appliance_running_above_watts    INT,
    ADD COLUMN appliance_idle_below_watts       INT,
    ADD COLUMN appliance_min_run_minutes        INT,
    ADD COLUMN appliance_finished_after_minutes INT,
    ADD COLUMN appliance_reminder_every_minutes INT,
    ADD COLUMN appliance_max_reminders          INT,
    ADD COLUMN appliance_quiet_from             TIME,
    ADD COLUMN appliance_quiet_to               TIME;

ALTER TABLE devices
    ADD CONSTRAINT devices_appliance_of_appliances
        CHECK (CASE
                   WHEN kind = 'APPLIANCE' THEN num_nulls(
                       appliance_running_above_watts, appliance_idle_below_watts, appliance_min_run_minutes,
                       appliance_finished_after_minutes, appliance_reminder_every_minutes, appliance_max_reminders,
                       appliance_quiet_from, appliance_quiet_to) = 0
                   ELSE num_nonnulls(
                       appliance_running_above_watts, appliance_idle_below_watts, appliance_min_run_minutes,
                       appliance_finished_after_minutes, appliance_reminder_every_minutes, appliance_max_reminders,
                       appliance_quiet_from, appliance_quiet_to) = 0
            END);

ALTER TABLE devices
    ADD CONSTRAINT devices_appliance_range
        CHECK (appliance_running_above_watts BETWEEN 5 AND 3000
            AND appliance_idle_below_watts BETWEEN 0 AND 100
            AND appliance_min_run_minutes BETWEEN 1 AND 180
            AND appliance_finished_after_minutes BETWEEN 1 AND 60
            AND appliance_reminder_every_minutes BETWEEN 5 AND 180
            AND appliance_max_reminders BETWEEN 0 AND 10
            AND extract(SECOND FROM appliance_quiet_from) = 0
            AND extract(SECOND FROM appliance_quiet_to) = 0);

ALTER TABLE devices
    ADD CONSTRAINT devices_appliance_idle_below_running
        CHECK (appliance_idle_below_watts < appliance_running_above_watts);
