-- Daily expiry digest (M3). One recipient per store for now; app logins and roles come in M8.

ALTER TABLE location
    -- Where the store's morning digest goes. NULL means the store gets no digest.
    ADD COLUMN digest_email   VARCHAR(254),
    -- The store-local date of the last digest run, so each store gets at most one per day.
    ADD COLUMN digest_sent_on DATE;

CREATE INDEX ix_location_digest ON location (id) WHERE digest_email IS NOT NULL;
