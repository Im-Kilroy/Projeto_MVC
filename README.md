## Sistema de Gerenciamento de Estoque

O projeto consiste em um gerenciador de estoque de um mercado,
podendo gerar relatório em PDF das mercadorias, pesquisar cada produto individualmente, verificar responsáveis, data de validade e etc...

Acompanha um bot agente do lado direito inferior da interface, facilitando a navegação do cliente.

Estrutura em MVC (Model-View-Controller).

As seguintes tecnologias são utilizadas:

> Java, H2 (SQL), Maven, HTML, CSS, DOCKER e JAVASCRIPT

<p align="center">
  <img src="https://drive.google.com/file/d/1-iwLQwbqXjQk_AwKagIJUxiifsXSXRM_/view?usp=drive_link" width="400">
  <br>
    <sub>Nova Interface</sub>
</p>


[▶️ Demonstração da aplicação](https://www.youtube.com/watch?v=h7uqkj7ofh8)

## Rodar localmente

> mvn compile -Dexec.mainClass="br.com.renata.Application"

> mvn exec:java

## Rodar no Docker

> docker compose up --build

## Configurações adicionais

>Configurar variável GEMINI_API_KEY localmente no env.


