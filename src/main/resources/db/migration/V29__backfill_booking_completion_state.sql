-- V24 added completion_state without backfilling rows cancelled before the
-- money-model deploy. Back then a cancel inside the window was rejected
-- outright, so every legacy CANCELLED booking was an in-window normal cancel.
-- Without this backfill those rows have completion_state = NULL, which
-- countConsumedLessons treats as "consumed", wrongly burning a lesson credit
-- and blocking re-booking / shrinking refunds.
UPDATE bookings
SET completion_state = 'CANCELLED_NORMAL'
WHERE status = 'CANCELLED'
  AND completion_state IS NULL;
