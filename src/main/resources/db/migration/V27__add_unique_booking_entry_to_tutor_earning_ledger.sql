-- Defect #5: settlement double-counting backstop.
--
-- A tutor must be credited at most once per (booking, entry_type). The service
-- already guards with findFirstByBookingId + the booking.settled flag, but a
-- race (two concurrent settlement runs) could slip a second LESSON_EARNED row
-- past the read guard. This unique constraint makes the double-credit physically
-- impossible: the second insert fails and the transaction rolls back.
--
-- Withdrawal / adjustment rows carry booking_id = NULL. In both MySQL and H2 a
-- UNIQUE index treats each NULL as distinct, so multiple NULL-booking rows
-- (holds, reverses, paybacks) coexist freely under this constraint.
ALTER TABLE tutor_earning_ledger
    ADD CONSTRAINT uk_tutor_earning_ledger_booking_entry UNIQUE (booking_id, entry_type);
