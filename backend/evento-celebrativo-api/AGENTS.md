# Projeto

Backend do projeto `api-evento-celebrativo`, uma API REST para gerenciamento de eventos celebrativos, pessoas, locais, perfis de acesso e escalas.

# Stack

* Java 21
* Spring Boot 3.4.5
* Maven Wrapper
* Spring Web, Spring Data JPA, Bean Validation, Spring Security, OAuth2 Authorization Server e Resource Server
* MapStruct para conversão entre Entity e DTO

# Comandos oficiais

No Windows, execute a partir da pasta `backend/evento-celebrativo-api`:

* `.\mvnw.cmd clean compile`
* `.\mvnw.cmd test`
* `.\mvnw.cmd -q test`
* `.\mvnw.cmd spring-boot:run`

Antes de concluir tarefas de código, rode `.\mvnw.cmd -q test` sempre que possível.

# Arquitetura

Use a divisão atual de pacotes:

* `controller`: entrada HTTP, status codes, validação do request e composição da resposta.
* `service`: contratos de regras de negócio.
* `service.impl`: implementação das regras de negócio e transações.
* `repository`: persistência via Spring Data JPA.
* `model`: entidades JPA.
* `dto.request`: dados de entrada da API.
* `dto.response`: dados de saída da API.
* `mapper`: conversão Entity <-> DTO, preferencialmente com MapStruct.
* `exception`: exceptions customizadas e tratamento global.

# Padrões do projeto

* Não exponha entidades JPA diretamente na API.
* Use DTOs para entrada e saída.
* Use mappers/MapStruct para conversão entre entidades e DTOs.
* Mantenha controllers focados em HTTP, status, validação e delegação.
* Mantenha services focados em regra de negócio.
* Use exceptions customizadas e `GlobalExceptionHandler` para respostas de erro consistentes.
* Preserve os endpoints existentes; não altere contratos já validados sem autorização.
* Para novos endpoints, evite camelCase no path e prefira nomes consistentes e legíveis.

# Segurança

* Login público atual: `POST /public/login`.
* Endpoints públicos atuais:
  * `GET /eventos`
  * `GET /eventos/{id}`
  * `GET /eventos/escala/eucaristia`
* Endpoints de pessoas e locais exigem autenticação:
  * `GET /locais`
  * `GET /leitores`
  * `GET /comentaristas`
  * `GET /ministrosDeEucaristia`
  * `GET /ministrosDaPalavra`
  * `GET /padres`
* Novos usuários cadastrados pela API recebem `ROLE_OPERATOR`.
* Apenas `ROLE_ADMIN` pode alterar roles em `PUT /pessoas/{id}/roles`.
* Apenas `ROLE_ADMIN` pode criar, alterar ou deletar recursos administrativos.
* Apenas `ROLE_ADMIN` pode criar evento com escala em `POST /eventos/com-escala`.
* Apenas `ROLE_ADMIN` pode alterar escala em `PUT /eventos/{id}/escala`.
* Não remova a segurança global nem desabilite autenticação para fazer testes passarem.

# Testes

* Testes de service usam Mockito.
* Testes de controller usam MockMvc.
* Testes de repository usam `@DataJpaTest`.
* Use `@MockitoBean`, não `@MockBean`.
* Cubra regras de autorização quando alterar endpoints ou segurança.
* Rode `.\mvnw.cmd -q test` antes de concluir tarefas sempre que possível.

# Pendências futuras

Documente e trate em tarefas próprias, sem implementar junto com mudanças não relacionadas:

* Separar profiles `test`, `dev` e `prod`.
* Remover secrets default antes de ambiente real.
* Remover hardcoded `localhost` do `PublicController` antes de integração/deploy.
* Avaliar Flyway ou Liquibase antes de MySQL real.
* Avaliar Swagger/OpenAPI depois de estabilizar a API.
* Avaliar JaCoCo e separação Failsafe/Surefire quando os testes crescerem.
