INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Luana Odinson', '34989374748', '1988-05-21', '123456', 'comentarista');
INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Miguel Souza', '34962165544', '1995-02-18', '123456', 'comentarista');
INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Helena Oliveira', '34991564562', '1999-09-06', '123456', 'comentarista');

INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Alice Lima', '34983246978', '1989-08-24', '123456', 'leitor');
INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Arthur Costa', '34978956324', '2005-03-24', '123456', 'leitor');
INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Heloísa Ribeiro', '34998632145', '1986-10-17', '123456', 'leitor');

INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Davi Gomes', '34963284523', '2003-06-02', '123456', 'ministro_da_palavra');
INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Laura Alves', '34998563215', '2006-07-11', '123456', 'ministro_da_palavra');
INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Bernardo Ferreira', '34936984562', '1982-12-08', '123456', 'ministro_da_palavra');

INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Mariana Ferraz', '34989374748', '1988-05-21', '123456', 'ministro_de_eucaristia');
INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Carlos Silva', '34991234567', '1975-11-10', '123456', 'ministro_de_eucaristia');
INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Fernanda Souza', '34987654321', '1992-03-25', '123456', 'ministro_de_eucaristia');

INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Padre Miguel', '34988776655', '1968-07-14', '123456', 'padre');
INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Padre Paulo', '34999887766', '1980-01-08', '123456', 'padre');
INSERT INTO tb_pessoa(nome, telefone, data_aniversario, password, tipo) VALUES ('Padre Roberto', '34981112233', '1972-09-03', '123456', 'padre');

INSERT INTO tb_role (authority) VALUES ('ROLE_OPERATOR');
INSERT INTO tb_role (authority) VALUES ('ROLE_ADMIN');

 INSERT INTO tb_pessoa_role (pessoa_id, role_id) VALUES (1, 1);
 INSERT INTO tb_pessoa_role (pessoa_id, role_id) VALUES (2, 1);
 INSERT INTO tb_pessoa_role (pessoa_id, role_id) VALUES (2, 2)


INSERT INTO tb_local(nome_da_igreja, endereco) VALUES ('Igreja Matriz Nossa Senhora do Rosário', 'Praça Rui Barbosa, Centro, Ibiá - MG');
INSERT INTO tb_local(nome_da_igreja, endereco) VALUES ('Paróquia São Sebastião', 'Rua São Sebastião, 123, Bairro Bela Vista, Ibiá - MG');
INSERT INTO tb_local(nome_da_igreja, endereco) VALUES ('Santuário de Santo Antônio', 'Avenida Padre João Rodrigues, 456, Bairro Centro, Ibiá - MG');

INSERT INTO tb_evento_celebrativo(nome_missa_ou_evento, data_evento, hora_evento, missa_ou_celebracao) VALUES ('Missa de Domingo da manhã', '2025-07-13', '10:00:00', TRUE);
INSERT INTO tb_evento_celebrativo(nome_missa_ou_evento, data_evento, hora_evento, missa_ou_celebracao) VALUES ('Celebração da Palavra de Sábado', '2025-07-12', '19:30:00', FALSE);
INSERT INTO tb_evento_celebrativo(nome_missa_ou_evento, data_evento, hora_evento, missa_ou_celebracao) VALUES ('Missa de Ação de Graças', '2025-07-20', '08:00:00', TRUE);

INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (1, 13);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (1, 10);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (1, 11);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (1, 4);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (1, 5);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (1, 1);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (1, 7);

INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (2, 7);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (2, 8);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (2, 4);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (2, 6);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (2, 10);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (2, 12);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (2, 2);

INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (3, 14);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (3, 11);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (3, 12);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (3, 5);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (3, 6);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (3, 3);
INSERT INTO tb_evento_pessoa (evento_id, pessoa_id) VALUES (3, 9);

INSERT INTO tb_evento_local (evento_id, local_id) VALUES (1, 1);
INSERT INTO tb_evento_local (evento_id, local_id) VALUES (2, 2);
INSERT INTO tb_evento_local (evento_id, local_id) VALUES (3, 3);