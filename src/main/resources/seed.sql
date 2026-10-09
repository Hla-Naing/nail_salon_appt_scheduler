INSERT INTO users (name, username, password_hash, role)
VALUES
    ('Maya Chen', 'maya', '$2a$10$VrIStGlGMM0KiC51OrecVOAMDZZ/soTTM.58gUp1Ovq9E1wa3LWkm', 'CUSTOMER'),
    ('Anna Lee', 'anna', '$2a$10$VrIStGlGMM0KiC51OrecVOAMDZZ/soTTM.58gUp1Ovq9E1wa3LWkm', 'PROVIDER'),
    ('Sofia Kim', 'sofia', '$2a$10$VrIStGlGMM0KiC51OrecVOAMDZZ/soTTM.58gUp1Ovq9E1wa3LWkm', 'PROVIDER'),
    ('Lily Park', 'lily', '$2a$10$VrIStGlGMM0KiC51OrecVOAMDZZ/soTTM.58gUp1Ovq9E1wa3LWkm', 'CUSTOMER')
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

-- Seed one fixed demo batch only when a provider has no slots at all.
-- Restarting tomorrow does not add another batch or revive removed slots.
INSERT INTO availability_slots (provider_id, service_id, start_at, end_at)
SELECT p.provider_id, s.service_id,
       ((CURRENT_DATE + v.days_ahead) + v.start_time) AT TIME ZONE 'America/Los_Angeles',
       ((CURRENT_DATE + v.days_ahead) + v.start_time) AT TIME ZONE 'America/Los_Angeles'
           + s.duration_minutes * INTERVAL '1 minute'
FROM (VALUES
    ('anna', 'Basic Manicure', 2, TIME '10:00'),
    ('anna', 'Nail Art', 2, TIME '11:00'),
    ('sofia', 'Gel Manicure', 3, TIME '10:00'),
    ('sofia', 'Basic Manicure', 3, TIME '11:30')
) AS v(username, service_name, days_ahead, start_time)
JOIN users u ON u.username = v.username
JOIN providers p ON p.user_id = u.user_id
JOIN services s ON s.name = v.service_name
WHERE NOT EXISTS (SELECT 1 FROM availability_slots existing WHERE existing.provider_id = p.provider_id);
