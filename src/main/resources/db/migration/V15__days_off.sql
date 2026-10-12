-- The days off the household adds to the calendar of the house (HAS-224): a day between a holiday
-- and a weekend, a day everybody stays at home. The weekends and the statutory holidays are not
-- stored - the service works them out (PolishHolidays) - so this table holds only what no rule
-- knows. One row per day, the day is the key: setting a day twice changes its reason.
-- The length of the reason is the bound of CalendarDay.reason in the SDK.
CREATE TABLE days_off
(
    date       DATE PRIMARY KEY,
    reason     VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

ALTER TABLE days_off
    ADD CONSTRAINT days_off_reason_not_blank CHECK (btrim(reason) <> '');
