-- Repeatable migration (Flyway reaplica automaticamente sempre que o checksum deste arquivo
-- muda). Um banco local ja pode ter a versao anterior deste seed aplicada, entao toda insercao
-- abaixo precisa ser guardada por "WHERE NOT EXISTS" usando uma chave natural do fixture
-- (phone_number, username, nomes de evento/local, pares (event_id, person_id)/(event_id,
-- location_id)) em vez de assumir que o script roda uma unica vez. Isso evita duplicar Person,
-- PersonMinistry, UserAccount, UserAccountRole, eventos, locations e assignments quando o
-- Flyway reexecuta o script inteiro em um banco ja populado.

INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000001', 'Luana Odinson', '34989374748', '1988-05-21'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34989374748');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000002', 'Miguel Souza', '34962165544', '1995-02-18'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34962165544');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000003', 'Helena Oliveira', '34991564562', '1999-09-06'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34991564562');

INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000004', 'Alice Lima', '34983246978', '1989-08-24'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34983246978');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000005', 'Arthur Costa', '34978956324', '2005-03-24'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34978956324');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000006', 'Heloísa Ribeiro', '34998632145', '1986-10-17'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34998632145');

INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000007', 'Davi Gomes', '34963284523', '2003-06-02'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34963284523');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000008', 'Laura Alves', '34998563215', '2006-07-11'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34998563215');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000009', 'Bernardo Ferreira', '34936984562', '1982-12-08'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34936984562');

INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-00000000000a', 'Mariana Ferraz', '34989374749', '1988-05-21'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34989374749');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-00000000000b', 'Carlos Silva', '34991234567', '1975-11-10'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34991234567');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-00000000000c', 'Fernanda Souza', '34987654321', '1992-03-25'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34987654321');

INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-00000000000d', 'Padre Miguel', '34988776655', '1968-07-14'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34988776655');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-00000000000e', 'Padre Paulo', '34999887766', '1980-01-08'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34999887766');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-00000000000f', 'Padre Roberto', '34981112233', '1972-09-03'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34981112233');

-- Pessoas novas: cobrem o cenario "accountExists=false" / role=null de cada ministry no
-- frontend. Nunca recebem tb_user_account nem tb_user_account_role.
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000010', 'Camila Martins', '34991110001', '1990-04-12'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34991110001');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000011', 'Gabriel Santos', '34991110002', '1993-11-08'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34991110002');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000012', 'Rafael Moreira', '34991110003', '1985-02-27'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34991110003');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000013', 'Juliana Mendes', '34991110004', '1998-06-15'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34991110004');
INSERT INTO tb_person (public_id, name, phone_number, birthday_date)
SELECT '00000000-0000-0000-0000-000000000014', 'Padre Antônio', '34991110005', '1965-09-30'
WHERE NOT EXISTS (SELECT 1 FROM tb_person WHERE phone_number = '34991110005');

INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'COMENTARISTAS'
WHERE p.phone_number = '34989374748'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'COMENTARISTAS'
WHERE p.phone_number = '34962165544'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'COMENTARISTAS'
WHERE p.phone_number = '34991564562'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);

INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'LEITORES'
WHERE p.phone_number = '34983246978'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'LEITORES'
WHERE p.phone_number = '34978956324'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'LEITORES'
WHERE p.phone_number = '34998632145'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);

INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'MINISTROS DA PALAVRA'
WHERE p.phone_number = '34963284523'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'MINISTROS DA PALAVRA'
WHERE p.phone_number = '34998563215'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'MINISTROS DA PALAVRA'
WHERE p.phone_number = '34936984562'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);

INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'MINISTROS DA EUCARISTIA'
WHERE p.phone_number = '34989374749'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'MINISTROS DA EUCARISTIA'
WHERE p.phone_number = '34991234567'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'MINISTROS DA EUCARISTIA'
WHERE p.phone_number = '34987654321'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);

INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'PRESBITEROS'
WHERE p.phone_number = '34988776655'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'PRESBITEROS'
WHERE p.phone_number = '34999887766'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'PRESBITEROS'
WHERE p.phone_number = '34981112233'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);

INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'COMENTARISTAS'
WHERE p.phone_number = '34991110001'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'LEITORES'
WHERE p.phone_number = '34991110002'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'MINISTROS DA PALAVRA'
WHERE p.phone_number = '34991110003'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'MINISTROS DA EUCARISTIA'
WHERE p.phone_number = '34991110004'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);
INSERT INTO tb_person_ministry (person_id, ministry_id, active, created_at, updated_at)
SELECT p.id, m.id, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tb_person p
JOIN tb_ministry m ON m.normalized_name = 'PRESBITEROS'
WHERE p.phone_number = '34991110005'
  AND NOT EXISTS (SELECT 1 FROM tb_person_ministry pm WHERE pm.person_id = p.id AND pm.ministry_id = m.id);

-- tb_user_account/tb_user_account_role sao a unica fonte de credenciais/autorizacao (Person nao
-- carrega mais password nem roles desde V18). Cada conta recebe exatamente uma role, escolhida
-- por ministry apenas como exemplo de fixture para o frontend testar admin/operator/sem-conta -
-- isso NAO expressa nenhuma regra de dominio ligando Ministry a Role. Cinco pessoas (uma por
-- ministry) permanecem sem tb_user_account para cobrir accountExists=false.
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34989374748'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34962165544'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34991564562'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34983246978'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34978956324'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34998632145'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34963284523'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34998563215'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34936984562'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34989374749'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34991234567'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34987654321'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34988776655'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34999887766'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);
INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
SELECT p.id, p.phone_number, '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0)
FROM tb_person p
WHERE p.phone_number = '34981112233'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id);

-- Role final desejada por conta (1 admin + 2 operator por ministry). O INSERT so dispara na
-- primeira vez que a conta existe sem role (banco limpo); os UPDATE de promocao logo abaixo sao
-- o mecanismo que garante o mesmo resultado quando o banco ja tinha a role antiga aplicada por
-- uma execucao anterior deste repeatable.
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_ADMIN'
WHERE p.phone_number = '34989374748'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_OPERATOR'
WHERE p.phone_number = '34962165544'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_OPERATOR'
WHERE p.phone_number = '34991564562'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_ADMIN'
WHERE p.phone_number = '34983246978'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_OPERATOR'
WHERE p.phone_number = '34978956324'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_OPERATOR'
WHERE p.phone_number = '34998632145'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_ADMIN'
WHERE p.phone_number = '34963284523'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_OPERATOR'
WHERE p.phone_number = '34998563215'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_OPERATOR'
WHERE p.phone_number = '34936984562'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_ADMIN'
WHERE p.phone_number = '34989374749'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_OPERATOR'
WHERE p.phone_number = '34991234567'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_OPERATOR'
WHERE p.phone_number = '34987654321'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_OPERATOR'
WHERE p.phone_number = '34988776655'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_ADMIN'
WHERE p.phone_number = '34999887766'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);
INSERT INTO tb_user_account_role (user_account_id, role_id)
SELECT ua.id, r.id
FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id JOIN tb_role r ON r.authority = 'ROLE_OPERATOR'
WHERE p.phone_number = '34981112233'
  AND NOT EXISTS (SELECT 1 FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id);

-- Promocao ROLE_OPERATOR -> ROLE_ADMIN das 4 contas indicadas. Padre Paulo ja e ROLE_ADMIN e nao
-- entra aqui. UPDATE e naturalmente idempotente (reaplicar so mantem ROLE_ADMIN) e e o unico
-- passo que garante o resultado final em um banco onde a role antiga ja tinha sido inserida por
-- uma execucao anterior deste repeatable.
UPDATE tb_user_account_role
SET role_id = (SELECT id FROM tb_role WHERE authority = 'ROLE_ADMIN')
WHERE user_account_id = (
    SELECT ua.id FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id WHERE p.phone_number = '34989374748'
);
UPDATE tb_user_account_role
SET role_id = (SELECT id FROM tb_role WHERE authority = 'ROLE_ADMIN')
WHERE user_account_id = (
    SELECT ua.id FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id WHERE p.phone_number = '34983246978'
);
UPDATE tb_user_account_role
SET role_id = (SELECT id FROM tb_role WHERE authority = 'ROLE_ADMIN')
WHERE user_account_id = (
    SELECT ua.id FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id WHERE p.phone_number = '34963284523'
);
UPDATE tb_user_account_role
SET role_id = (SELECT id FROM tb_role WHERE authority = 'ROLE_ADMIN')
WHERE user_account_id = (
    SELECT ua.id FROM tb_user_account ua JOIN tb_person p ON p.id = ua.person_id WHERE p.phone_number = '34989374749'
);

INSERT INTO tb_location (church_name, address)
SELECT 'Igreja Matriz Nossa Senhora do Rosário', 'Praça Rui Barbosa, Centro, Ibiá - MG'
WHERE NOT EXISTS (SELECT 1 FROM tb_location WHERE church_name = 'Igreja Matriz Nossa Senhora do Rosário');
INSERT INTO tb_location (church_name, address)
SELECT 'Paróquia São Sebastião', 'Rua São Sebastião, 123, Bairro Bela Vista, Ibiá - MG'
WHERE NOT EXISTS (SELECT 1 FROM tb_location WHERE church_name = 'Paróquia São Sebastião');
INSERT INTO tb_location (church_name, address)
SELECT 'Santuário de Santo Antônio', 'Avenida Padre João Rodrigues, 456, Bairro Centro, Ibiá - MG'
WHERE NOT EXISTS (SELECT 1 FROM tb_location WHERE church_name = 'Santuário de Santo Antônio');

INSERT INTO tb_celebration_event (name_mass_or_event, start_at, end_at, mass_or_celebration)
SELECT 'Missa de Domingo da manhã', '2025-07-13 10:00:00', '2025-07-13 11:00:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM tb_celebration_event WHERE name_mass_or_event = 'Missa de Domingo da manhã' AND start_at = '2025-07-13 10:00:00');
INSERT INTO tb_celebration_event (name_mass_or_event, start_at, end_at, mass_or_celebration)
SELECT 'Celebração da Palavra de Sábado', '2025-07-12 19:30:00', '2025-07-12 20:30:00', FALSE
WHERE NOT EXISTS (SELECT 1 FROM tb_celebration_event WHERE name_mass_or_event = 'Celebração da Palavra de Sábado' AND start_at = '2025-07-12 19:30:00');
INSERT INTO tb_celebration_event (name_mass_or_event, start_at, end_at, mass_or_celebration)
SELECT 'Missa de Ação de Graças', '2025-07-20 08:00:00', '2025-07-20 09:00:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM tb_celebration_event WHERE name_mass_or_event = 'Missa de Ação de Graças' AND start_at = '2025-07-20 08:00:00');

INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 1, 13, 'PRIEST', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 1 AND person_id = 13);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 1, 10, 'EUCHARISTIC_MINISTER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 1 AND person_id = 10);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 1, 11, 'EUCHARISTIC_MINISTER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 1 AND person_id = 11);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 1, 4, 'READER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 1 AND person_id = 4);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 1, 5, 'READER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 1 AND person_id = 5);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 1, 1, 'COMMENTATOR', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 1 AND person_id = 1);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 1, 7, 'MINISTER_OF_THE_WORD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 1 AND person_id = 7);

INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 2, 7, 'MINISTER_OF_THE_WORD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 2 AND person_id = 7);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 2, 8, 'MINISTER_OF_THE_WORD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 2 AND person_id = 8);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 2, 4, 'READER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 2 AND person_id = 4);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 2, 6, 'READER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 2 AND person_id = 6);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 2, 10, 'EUCHARISTIC_MINISTER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 2 AND person_id = 10);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 2, 12, 'EUCHARISTIC_MINISTER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 2 AND person_id = 12);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 2, 2, 'COMMENTATOR', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 2 AND person_id = 2);

INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 3, 14, 'PRIEST', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 3 AND person_id = 14);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 3, 11, 'EUCHARISTIC_MINISTER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 3 AND person_id = 11);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 3, 12, 'EUCHARISTIC_MINISTER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 3 AND person_id = 12);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 3, 5, 'READER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 3 AND person_id = 5);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 3, 6, 'READER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 3 AND person_id = 6);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 3, 3, 'COMMENTATOR', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 3 AND person_id = 3);
INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
SELECT 3, 9, 'MINISTER_OF_THE_WORD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM tb_event_assignment WHERE event_id = 3 AND person_id = 9);

INSERT INTO tb_event_location (event_id, location_id)
SELECT 1, 1
WHERE NOT EXISTS (SELECT 1 FROM tb_event_location WHERE event_id = 1 AND location_id = 1);
INSERT INTO tb_event_location (event_id, location_id)
SELECT 2, 2
WHERE NOT EXISTS (SELECT 1 FROM tb_event_location WHERE event_id = 2 AND location_id = 2);
INSERT INTO tb_event_location (event_id, location_id)
SELECT 3, 3
WHERE NOT EXISTS (SELECT 1 FROM tb_event_location WHERE event_id = 3 AND location_id = 3);
