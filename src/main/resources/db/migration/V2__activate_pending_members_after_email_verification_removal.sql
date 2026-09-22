UPDATE member
SET status = 'ACTIVE'
WHERE status = 'PENDING_VERIFICATION'
  AND deleted_at IS NULL;
