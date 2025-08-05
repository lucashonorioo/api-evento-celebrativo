INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Luana Odinson', '34989374748', '1988-05-21', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'commentator');
INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Miguel Souza', '34962165544', '1995-02-18', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'commentator');
INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Helena Oliveira', '34991564562', '1999-09-06', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'commentator');

INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Alice Lima', '34983246978', '1989-08-24', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'reader');
INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Arthur Costa', '34978956324', '2005-03-24', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'reader');
INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Heloísa Ribeiro', '34998632145', '1986-10-17', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'reader');

INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Davi Gomes', '34963284523', '2003-06-02', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'minister_of_the_word');
INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Laura Alves', '34998563215', '2006-07-11', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'minister_of_the_word');
INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Bernardo Ferreira', '34936984562', '1982-12-08', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'minister_of_the_word');

INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Mariana Ferraz', '34989374748', '1988-05-21', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'eucharistic_minister');
INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Carlos Silva', '34991234567', '1975-11-10', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'eucharistic_minister');
INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Fernanda Souza', '34987654321', '1992-03-25', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'eucharistic_minister');

INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Padre Miguel', '34988776655', '1968-07-14', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'priest');
INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Padre Paulo', '34999887766', '1980-01-08', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'priest');
INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type) VALUES ('Padre Roberto', '34981112233', '1972-09-03', '$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW', 'priest');



INSERT INTO tb_role (authority) VALUES ('ROLE_OPERATOR');
INSERT INTO tb_role (authority) VALUES ('ROLE_ADMIN');

INSERT INTO tb_person_role (person_id, role_id) VALUES (1, 1);
INSERT INTO tb_person_role (person_id, role_id) VALUES (1, 2);
INSERT INTO tb_person_role (person_id, role_id) VALUES (2, 1);
INSERT INTO tb_person_role (person_id, role_id) VALUES (3, 1);

INSERT INTO tb_person_role (person_id, role_id) VALUES (4, 1);
INSERT INTO tb_person_role (person_id, role_id) VALUES (4, 2)
INSERT INTO tb_person_role (person_id, role_id) VALUES (5, 1);
INSERT INTO tb_person_role (person_id, role_id) VALUES (6, 1);

INSERT INTO tb_person_role (person_id, role_id) VALUES (7, 1)
INSERT INTO tb_person_role (person_id, role_id) VALUES (7, 2);
INSERT INTO tb_person_role (person_id, role_id) VALUES (8, 1);
INSERT INTO tb_person_role (person_id, role_id) VALUES (9, 1);

INSERT INTO tb_person_role (person_id, role_id) VALUES (10, 1);
INSERT INTO tb_person_role (person_id, role_id) VALUES (10, 2);
INSERT INTO tb_person_role (person_id, role_id) VALUES (11, 1);
INSERT INTO tb_person_role (person_id, role_id) VALUES (12, 1);

INSERT INTO tb_person_role (person_id, role_id) VALUES (13, 1);
INSERT INTO tb_person_role (person_id, role_id) VALUES (13, 2);
INSERT INTO tb_person_role (person_id, role_id) VALUES (14, 2);
INSERT INTO tb_person_role (person_id, role_id) VALUES (15, 1);


INSERT INTO tb_location(church_name, address) VALUES ('Igreja Matriz Nossa Senhora do Rosário', 'Praça Rui Barbosa, Centro, Ibiá - MG');
INSERT INTO tb_location(church_name, address) VALUES ('Paróquia São Sebastião', 'Rua São Sebastião, 123, Bairro Bela Vista, Ibiá - MG');
INSERT INTO tb_location(church_name, address) VALUES ('Santuário de Santo Antônio', 'Avenida Padre João Rodrigues, 456, Bairro Centro, Ibiá - MG');

INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration) VALUES ('Missa de Domingo da manhã', '2025-07-13', '10:00:00', TRUE);
INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration) VALUES ('Celebração da Palavra de Sábado', '2025-07-12', '19:30:00', FALSE);
INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration) VALUES ('Missa de Ação de Graças', '2025-07-20', '08:00:00', TRUE);


INSERT INTO tb_event_person (event_id, person_id) VALUES (1, 13);
INSERT INTO tb_event_person (event_id, person_id) VALUES (1, 10);
INSERT INTO tb_event_person (event_id, person_id) VALUES (1, 11);
INSERT INTO tb_event_person (event_id, person_id) VALUES (1, 4);
INSERT INTO tb_event_person (event_id, person_id) VALUES (1, 5);
INSERT INTO tb_event_person (event_id, person_id) VALUES (1, 1);
INSERT INTO tb_event_person (event_id, person_id) VALUES (1, 7);

INSERT INTO tb_event_person (event_id, person_id) VALUES (2, 7);
INSERT INTO tb_event_person (event_id, person_id) VALUES (2, 8);
INSERT INTO tb_event_person (event_id, person_id) VALUES (2, 4);
INSERT INTO tb_event_person (event_id, person_id) VALUES (2, 6);
INSERT INTO tb_event_person (event_id, person_id) VALUES (2, 10);
INSERT INTO tb_event_person (event_id, person_id) VALUES (2, 12);
INSERT INTO tb_event_person (event_id, person_id) VALUES (2, 2);

INSERT INTO tb_event_person (event_id, person_id) VALUES (3, 14);
INSERT INTO tb_event_person (event_id, person_id) VALUES (3, 11);
INSERT INTO tb_event_person (event_id, person_id) VALUES (3, 12);
INSERT INTO tb_event_person (event_id, person_id) VALUES (3, 5);
INSERT INTO tb_event_person (event_id, person_id) VALUES (3, 6);
INSERT INTO tb_event_person (event_id, person_id) VALUES (3, 3);
INSERT INTO tb_event_person (event_id, person_id) VALUES (3, 9);

INSERT INTO tb_event_location (event_id, location_id) VALUES (1, 1);
INSERT INTO tb_event_location (event_id, location_id) VALUES (2, 2);
INSERT INTO tb_event_location (event_id, location_id) VALUES (3, 3);