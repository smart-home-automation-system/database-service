-- The household requests (HAS-409, smart-home-sdk 1.17.0 MemberRequest): an errand one member writes for
-- another - "buy bread" - which notification-service texts to its recipient once, the next morning at
-- 08:00, and which stays on the list until somebody ticks it off in the dashboard.
--
-- A table of its own and never a column of household_members, the rule of push_subscriptions (V19): a
-- whole write of a member cannot drop the list. The recipient is referenced by its id, not by its name
-- as HAS-409 first planned: the name is unique by an index on upper(name) (V10), which a foreign key
-- cannot reference, and the id keeps the list through a rename all the same. A member removed takes the
-- list with it.
--
-- author and done_by are names as the registry spelled them when the row was written: history, not a
-- reference - a later rename of the author leaves the old spelling. The bounds are those of the SDK
-- model, repeated as checks (the rule of V8 / V9): one line of text with something else than spaces in it,
-- the names of the registry, and done_at with done_by, or neither.
--
-- No scheduler here: the done requests of a member older than 30 days are deleted by the next write to
-- that member's list.

CREATE TABLE member_requests
(
    id         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id  INT          NOT NULL REFERENCES household_members (id) ON DELETE CASCADE,
    text       VARCHAR(100) NOT NULL,
    author     VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    sent_at    TIMESTAMP,
    done_at    TIMESTAMP,
    done_by    VARCHAR(50)
);

ALTER TABLE member_requests
    ADD CONSTRAINT member_requests_text_one_line
        CHECK (char_length(text) BETWEEN 1 AND 100 AND text ~ '\S' AND text !~ '[[:cntrl:]]');

ALTER TABLE member_requests
    ADD CONSTRAINT member_requests_names
        CHECK (char_length(author) BETWEEN 3 AND 50 AND (done_by IS NULL OR char_length(done_by) BETWEEN 3 AND 50));

ALTER TABLE member_requests
    ADD CONSTRAINT member_requests_done_together CHECK ((done_at IS NULL) = (done_by IS NULL));

-- the list of a member (open first by done_at null) and the purge of its done requests
CREATE INDEX member_requests_member_done_idx ON member_requests (member_id, done_at);
