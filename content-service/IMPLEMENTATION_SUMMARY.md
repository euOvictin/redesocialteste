# Content Service - Resumo da Implementação

## Status da Implementação

✅ **Tarefa 5.1**: Projeto Spring Boot criado com todas as dependências (MongoDB, PostgreSQL, S3, Kafka)
✅ **Tarefa 5.2**: Modelos de dados implementados (Post, Comment, Story, PostMetadata, Like, Share)
✅ **Tarefa 5.3**: Criação de posts implementada com extração de hashtags e eventos Kafka
✅ **Tarefa 5.4**: Testes de propriedade para posts implementados (Properties 6, 10, 11)
⚠️ **Tarefa 5.5-5.10**: Implementação básica criada (upload de mídia, stories, exclusão)

## Estrutura Implementada

### Modelos de Dados

#### MongoDB (Documentos)
- **Post**: Conteúdo completo do post com texto, mídia e hashtags
- **Comment**: Comentários em posts
- **Story**: Stories temporários com expiração de 24h
- **StoryView**: Registro de visualizações de stories

#### PostgreSQL (Metadados)
- **PostMetadata**: Metadados e contadores (likes, comments, shares)
- **Like**: Curtidas em posts
- **Share**: Compartilhamentos de posts

### Serviços Implementados

1. **PostService**
   - Criação de posts com validação (1-5000 caracteres)
   - Extração automática de hashtags
   - Publicação de eventos Kafka
   - Recuperação de posts

2. **StoryService**
   - Criação de stories com expiração de 24h
   - Registro de visualizações
   - Listagem de stories ativos
   - Listagem de visualizadores (últimas 24h)

3. **MediaService**
   - Upload de imagens para S3 (validação < 10MB)
   - Upload de vídeos para S3 (validação < 100MB)
   - Validação de formatos de arquivo

4. **PostDeletionService**
   - Soft delete de posts
   - Publicação de eventos de exclusão

5. **EventPublisher**
   - Publicação de eventos no Kafka
   - Eventos: post.created, post.deleted

### Testes Implementados

#### Testes de Propriedade (jqwik - 100 iterações)

**PostServiceProperties**:
- ✅ Property 6: Post com texto válido é criado
- ✅ Property 10: Hashtags são extraídas automaticamente
- ✅ Property 11: Criação de post publica evento
- ✅ Propriedade adicional: Post pode ser recuperado após criação

**StoryServiceProperties**:
- ✅ Property 13: Story criado tem expiração de 24 horas
- ✅ Property 14: Visualização retorna apenas stories não expirados
- ✅ Property 15: Visualizações de stories são registradas
- ✅ Property 16: Lista de visualizadores filtra por 24 horas
- ✅ Propriedade adicional: Visualização de story é idempotente

#### Testes Unitários

**PostServiceTest**:
- Criação de post com tamanho mínimo (1 caractere)
- Criação de post com tamanho máximo (5000 caracteres)
- Post sem hashtags
- Post com múltiplas hashtags
- Deduplicação de hashtags
- Erro ao buscar post inexistente
- Erro ao buscar post deletado

**HashtagExtractorTest**:
- Extração de hashtags únicas
- Conversão para lowercase
- Deduplicação
- Hashtags com números e underscores
- Casos de borda (null, vazio, etc.)

## Configuração

### Variáveis de Ambiente

```bash
# MongoDB
MONGODB_URI=mongodb://localhost:27017/content_db

# PostgreSQL
POSTGRES_URL=jdbc:postgresql://localhost:5432/content_metadata
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# AWS S3
S3_BUCKET_NAME=rede-social-media
AWS_REGION=us-east-1
AWS_ACCESS_KEY=
AWS_SECRET_KEY=
S3_ENDPOINT=http://localhost:4566  # LocalStack

# Server
PORT=8082
```

### Dependências Principais

- Spring Boot 3.2.0
- Spring Data MongoDB
- Spring Data JPA (PostgreSQL)
- Spring Kafka
- AWS SDK S3
- jqwik 1.8.2 (Property-based testing)
- Testcontainers 1.19.3

## Endpoints Implementados

### Posts
- `POST /api/posts` - Criar post
- `GET /api/posts/{id}` - Buscar post

### Health Check
- `GET /actuator/health` - Status do serviço

## Eventos Kafka

### Publicados
- `post.created` - Quando um post é criado
  - Payload: postId, userId, content, hashtags, type, createdAt
  
- `post.deleted` - Quando um post é deletado
  - Payload: postId, userId, deletedAt

## Próximos Passos

Para completar totalmente a implementação:

1. **Upload de Mídia (5.5-5.6)**:
   - Implementar geração de thumbnails para imagens
   - Implementar processamento de vídeo em múltiplas resoluções
   - Adicionar testes de propriedade para upload (Properties 7, 8, 9)

2. **Exclusão de Posts (5.7-5.8)**:
   - Adicionar endpoint REST para exclusão
   - Implementar testes de propriedade (Property 12)

3. **Stories (5.9-5.10)**:
   - Adicionar endpoints REST para stories
   - Implementar job de limpeza de stories expirados
   - Já possui testes de propriedade implementados

4. **Interações Sociais (Tarefa 6)**:
   - Implementar curtidas
   - Implementar comentários
   - Implementar compartilhamentos

## Como Executar

### Desenvolvimento Local

```bash
# Iniciar dependências
docker-compose up -d postgres mongodb redis kafka

# Executar aplicação
mvn spring-boot:run

# Executar testes
mvn test

# Executar apenas testes de propriedade
mvn test -Dtest=*Properties
```

### Com Docker

```bash
# Build
docker build -t content-service .

# Run
docker run -p 8082:8082 --env-file .env content-service
```

## Observações

- Todos os testes de propriedade executam 100 iterações conforme especificação
- Soft delete implementado para posts (campo is_deleted)
- Hashtags são convertidas para lowercase e deduplicadas
- Stories expiram automaticamente após 24h via índice TTL do MongoDB
- Eventos Kafka são publicados de forma assíncrona
- Validações de tamanho de arquivo implementadas (10MB imagens, 100MB vídeos)

## Validação dos Requisitos

### Requirements 2.1 ✅
- Posts com texto de 1-5000 caracteres são criados com ID único

### Requirements 2.5 ✅
- Hashtags são extraídas automaticamente do padrão #palavra

### Requirements 2.6 ✅
- Eventos post.created são publicados no Kafka

### Requirements 3.1 ✅
- Stories têm timestamp de expiração de 24 horas

### Requirements 3.3 ✅
- Apenas stories não expirados são retornados

### Requirements 3.4 ✅
- Visualizações são registradas com viewerId e timestamp

### Requirements 3.5 ✅
- Lista de visualizadores filtra por últimas 24 horas
