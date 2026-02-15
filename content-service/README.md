# Content Service

Microsserviço responsável por gerenciar posts, stories, comentários e interações sociais (curtidas, compartilhamentos) na Rede Social Brasileira.

## Tecnologias

- **Java 17**
- **Spring Boot 3.2.0**
- **MongoDB** - Armazenamento de posts, comentários e stories
- **PostgreSQL** - Metadados de posts
- **Redis** - Cache
- **Apache Kafka** - Event streaming
- **AWS S3** - Armazenamento de mídia (imagens e vídeos)
- **jqwik** - Property-based testing

## Funcionalidades

### Posts
- Criação de posts com texto (1-5000 caracteres)
- Upload de imagens (JPEG, PNG, WebP < 10MB)
- Upload de vídeos (MP4, WebM < 100MB)
- Processamento de vídeo em múltiplas resoluções (480p, 720p, 1080p)
- Extração automática de hashtags
- Exclusão de posts (soft delete)

### Stories
- Publicação de stories com imagem/vídeo
- Expiração automática após 24 horas
- Registro de visualizações
- Listagem de visualizadores

### Interações
- Curtidas em posts
- Comentários (1-1000 caracteres)
- Compartilhamento de posts

### Eventos Kafka
- `post.created` - Publicado quando um post é criado
- `post.deleted` - Publicado quando um post é deletado
- `like.created` - Publicado quando um post é curtido
- `comment.created` - Publicado quando um comentário é adicionado
- `share.created` - Publicado quando um post é compartilhado

## Configuração

1. Copie o arquivo `.env.example` para `.env` e configure as variáveis de ambiente
2. Certifique-se de que MongoDB, PostgreSQL, Redis e Kafka estão rodando
3. Configure o AWS S3 ou use LocalStack para desenvolvimento local

## Executar

```bash
# Compilar
mvn clean package

# Executar
mvn spring-boot:run

# Executar com Docker
docker build -t content-service .
docker run -p 8082:8082 --env-file .env content-service
```

## Testes

```bash
# Executar todos os testes
mvn test

# Executar apenas testes unitários
mvn test -Dtest=*Test

# Executar apenas testes de propriedade
mvn test -Dtest=*Properties
```

## Endpoints

### Health Check
- `GET /actuator/health` - Status do serviço

### Posts
- `POST /api/posts` - Criar post
- `GET /api/posts/{id}` - Buscar post
- `DELETE /api/posts/{id}` - Deletar post
- `POST /api/posts/{id}/like` - Curtir post
- `DELETE /api/posts/{id}/like` - Descurtir post
- `POST /api/posts/{id}/comments` - Adicionar comentário
- `GET /api/posts/{id}/comments` - Listar comentários

### Stories
- `POST /api/stories` - Criar story
- `GET /api/stories/user/{userId}` - Listar stories de um usuário
- `DELETE /api/stories/{id}` - Deletar story
- `POST /api/stories/{id}/view` - Registrar visualização
- `GET /api/stories/{id}/viewers` - Listar visualizadores

### Media Upload
- `POST /api/posts/media/image` - Upload de imagem com geração de thumbnail
- `POST /api/posts/media/video` - Upload de vídeo com processamento de resoluções

## Arquitetura

```
content-service/
├── src/
│   ├── main/
│   │   ├── java/com/redesocial/contentservice/
│   │   │   ├── config/          # Configurações (S3, Kafka, etc)
│   │   │   ├── controller/      # REST Controllers
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   ├── exception/       # Exception handlers
│   │   │   ├── model/           # Domain models
│   │   │   │   ├── mongo/       # MongoDB documents
│   │   │   │   └── jpa/         # JPA entities
│   │   │   ├── repository/      # Data repositories
│   │   │   │   ├── mongo/       # MongoDB repositories
│   │   │   │   └── jpa/         # JPA repositories
│   │   │   ├── service/         # Business logic
│   │   │   └── util/            # Utilities
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/    # Flyway migrations
│   └── test/
│       └── java/com/redesocial/contentservice/
│           ├── service/         # Unit and property tests
│           └── integration/     # Integration tests
└── pom.xml
```

## Banco de Dados

### MongoDB Collections
- `posts` - Posts completos com conteúdo e mídia
- `comments` - Comentários em posts
- `stories` - Stories temporários
- `story_views` - Visualizações de stories

### PostgreSQL Tables
- `post_metadata` - Metadados de posts (contadores, timestamps)
- `likes` - Curtidas em posts
- `shares` - Compartilhamentos de posts

## Desenvolvimento

Este serviço faz parte da arquitetura de microsserviços da Rede Social Brasileira. Para desenvolvimento local completo, use o `docker-compose.yml` na raiz do projeto.
