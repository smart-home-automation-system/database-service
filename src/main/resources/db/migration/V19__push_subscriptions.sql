-- The Web Push subscriptions of the browsers of the household (HAS-272, smart-home-sdk 1.13.0): what a
-- browser answers to PushManager.subscribe once the installed application asks it, stored per member.
-- notification-service reads the subscriptions of the members at home and posts each household
-- message to them.
--
-- A table of its own and never a column of household_members (owner, 2026-10-23): the subscriptions
-- are written by the browsers on endpoints of their own, so a whole write of a member cannot drop
-- them, and no read of the household hands the keys out. The endpoint tells two subscriptions apart
-- and is held once: a browser that subscribes again, for the same or for another member, is the same
-- row (the service writes INSERT ... ON CONFLICT). The bounds are those of PushSubscription and
-- PushSubscriptionKeys of the SDK; the keys are base64url as the browser gives them.
--
-- A member removed takes its subscriptions with it (the foreign key); a member switched off loses
-- them in the service. last_used_at is for the sender to come (HAS-276) and is left empty until then.

CREATE TABLE push_subscriptions
(
    id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id    INT           NOT NULL REFERENCES household_members (id) ON DELETE CASCADE,
    endpoint     VARCHAR(2000) NOT NULL,
    p256dh       VARCHAR(88)   NOT NULL,
    auth         VARCHAR(24)   NOT NULL,
    user_agent   VARCHAR(500),
    created_at   TIMESTAMP     NOT NULL,
    last_used_at TIMESTAMP
);

ALTER TABLE push_subscriptions
    ADD CONSTRAINT push_subscriptions_endpoint_uq UNIQUE (endpoint);

ALTER TABLE push_subscriptions
    ADD CONSTRAINT push_subscriptions_endpoint_https CHECK (endpoint ~ '^https://\S+$');

ALTER TABLE push_subscriptions
    ADD CONSTRAINT push_subscriptions_keys_base64url
        CHECK (p256dh ~ '^[A-Za-z0-9_-]{87}=?$' AND auth ~ '^[A-Za-z0-9_-]{22}(==)?$');

CREATE INDEX push_subscriptions_member_idx ON push_subscriptions (member_id);
