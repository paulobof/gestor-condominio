-- flyway:transactional=true

WITH new_admin AS (
    INSERT INTO "user" (
        unit_id, is_unit_master, full_name, greeting_name,
        password_hash, password_pepper_version, must_change_password, status,
        consent_document_version, consent_accepted_at, consent_accepted_ip
    ) VALUES (
        NULL, false, '${adminName}', '${adminName}',
        '__PENDING__', 1, true, 'ACTIVE',
        '1.0.0', now(), '127.0.0.1'::inet
    )
    RETURNING id
),
admin_email AS (
    INSERT INTO user_email (user_id, email, is_primary, verified_at)
    SELECT id, '${adminEmail}', true, now() FROM new_admin
    RETURNING user_id
)
INSERT INTO user_role (user_id, role_id)
SELECT user_id, 1 FROM admin_email;
