-- The reminders and the announcements of the household requests (HAS-415, smart-home-sdk 1.19.0, epic HAS-407):
-- notification-service reminds the recipient every evening at 20:00 while a texted request stays open, tells the
-- author within a minute when it was ticked off, and once when it is still open some days after its SMS. These
-- columns say when each was done, so a repeat after a cut-off sends nothing twice.
--
-- reminded_at       the last evening reminder that carried the request - only of a texted one
-- done_announced_at the author told it was ticked off - only of a done one; set with done_at when the author
--                   ticked it off themselves, so nobody is told about their own tick
-- stale_announced_at the author told it is still open - only of a texted one, once
--
-- The requests done before this migration count as announced: their authors are not told about them after
-- the deploy. Nullable and without a default, for the rollout: the released 0.23.0 names its own columns.

ALTER TABLE member_requests
    ADD COLUMN reminded_at        TIMESTAMP,
    ADD COLUMN done_announced_at  TIMESTAMP,
    ADD COLUMN stale_announced_at TIMESTAMP;

UPDATE member_requests SET done_announced_at = done_at WHERE done_at IS NOT NULL;

ALTER TABLE member_requests
    ADD CONSTRAINT member_requests_marks
        CHECK ((done_announced_at IS NULL OR done_at IS NOT NULL)
            AND (reminded_at IS NULL OR sent_at IS NOT NULL)
            AND (stale_announced_at IS NULL OR sent_at IS NOT NULL));
