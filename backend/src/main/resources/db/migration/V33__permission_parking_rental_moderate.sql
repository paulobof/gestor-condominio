-- flyway:transactional=true

-- Permissão de moderação do aluguel de vagas. Espelha CLASSIFIED_MODERATE:
-- concedida a MANAGER (role 1) e COUNCIL (role 2). id 20 = próximo livre (máx atual = 19).
INSERT INTO permission (id, code, label) VALUES
    (20, 'PARKING_RENTAL_MODERATE', 'Moderar aluguel de vagas');

INSERT INTO role_permission (role_id, permission_id) VALUES
    (1, 20),
    (2, 20);
