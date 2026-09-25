INSERT INTO users (name, username, password_hash, role)
VALUES
    ('Maya Chen', 'maya', 'DEMO_LOGIN_NOT_ENABLED', 'CUSTOMER'),
    ('Anna Lee', 'anna', 'DEMO_LOGIN_NOT_ENABLED', 'PROVIDER'),
    ('Sofia Kim', 'sofia', 'DEMO_LOGIN_NOT_ENABLED', 'PROVIDER')
ON CONFLICT (username) DO NOTHING;

INSERT INTO providers (user_id, specialty)
SELECT user_id, 'Nail art'
FROM users
WHERE username = 'anna'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO providers (user_id, specialty)
SELECT user_id, 'Gel nails'
FROM users
WHERE username = 'sofia'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO services (name, duration_minutes, price)
VALUES
    ('Basic Manicure', 30, 25.00),
    ('Gel Manicure', 60, 45.00),
    ('Nail Art', 90, 70.00)
ON CONFLICT (name) DO NOTHING;

INSERT INTO availability_slots (provider_id, service_id, start_at, end_at)
SELECT p.provider_id, s.service_id, v.start_at, v.end_at
FROM (
    VALUES
        ('anna',  'Basic Manicure', '2026-10-01 10:00:00-07'::timestamptz, '2026-10-01 10:30:00-07'::timestamptz),
        ('anna',  'Nail Art',        '2026-10-01 11:00:00-07'::timestamptz, '2026-10-01 12:30:00-07'::timestamptz),
        ('sofia', 'Gel Manicure',   '2026-10-02 10:00:00-07'::timestamptz, '2026-10-02 11:00:00-07'::timestamptz),
        ('sofia', 'Basic Manicure', '2026-10-02 11:30:00-07'::timestamptz, '2026-10-02 12:00:00-07'::timestamptz)
) AS v(username, service_name, start_at, end_at)
JOIN users u ON u.username = v.username
JOIN providers p ON p.user_id = u.user_id
JOIN services s ON s.name = v.service_name
WHERE NOT EXISTS (
    SELECT 1
    FROM availability_slots existing
    WHERE existing.provider_id = p.provider_id
      AND existing.service_id = s.service_id
      AND existing.start_at = v.start_at
);