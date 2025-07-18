# API Evento Celebrativo


## Diagrama de Classes

```mermaid

classDiagram
  class EventoCelebrativo {
    Long id
    String nomeDaMissaOuEvento
    LocalDate dataEvento
    LocalTime horaEvento
    Boolean missaOuCelebracao
  }

  class Local {
    Long id
    String nomeDaIgreja
    String endereco
  }

  class Pessoa {
    Long id
    String nome
    String telefone
    String dataAniversario
  }

  class Padre
  class MinistroDaPalavra
  class MinistroDeEucaristia
  class Leitor
  class Comentarista

  EventoCelebrativo "1..*" --> "1..*" Local
  EventoCelebrativo "1..*" --> "1..*" Pessoa

  Pessoa <|-- Padre
  Pessoa <|-- MinistroDaPalavra
  Pessoa <|-- MinistroDeEucaristia
  Pessoa <|-- Leitor
  Pessoa <|-- Comentarista

```
