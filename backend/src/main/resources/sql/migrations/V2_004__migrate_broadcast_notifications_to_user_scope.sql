INSERT INTO notifications (user_id, title, content, read_flag, created_at)
SELECT users.id, notifications.title, notifications.content, false, notifications.created_at
FROM notifications
JOIN users ON 1 = 1
WHERE notifications.user_id IS NULL;

DELETE FROM notifications
WHERE user_id IS NULL;
