-- Phone numbers move from the display format xxx-xxx-xxx to E.164 (+48505602702): they are SMS
-- recipients, and formatting for display belongs to the client. V8 is already applied on the
-- cluster database, so the change is a migration of its own, not an edit of V8.

ALTER TABLE household_members
    DROP CONSTRAINT household_members_phone_format;

ALTER TABLE household_members
    ALTER COLUMN phone TYPE VARCHAR(16);

-- every number stored so far is a Polish one in the old format
UPDATE household_members
SET phone = '+48' || replace(phone, '-', '')
WHERE phone ~ '^[0-9]{3}-[0-9]{3}-[0-9]{3}$';

ALTER TABLE household_members
    ADD CONSTRAINT household_members_phone_format CHECK (phone ~ '^\+[1-9][0-9]{7,14}$');
