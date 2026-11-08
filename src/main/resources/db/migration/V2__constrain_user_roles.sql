ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_role CHECK (role IN ('ROLE_ADMIN', 'ROLE_USER'));
