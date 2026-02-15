# Design Document - Rede Social Brasileira

## Overview

A Rede Social Brasileira é uma plataforma moderna de mídia social que combina características do Instagram e Twitter, construída com arquitetura de microsserviços para garantir escalabilidade, manutenibilidade e alta disponibilidade. A plataforma permite que usuários compartilhem conteúdo multimídia, interajam socialmente, e se comuniquem em tempo real.

### Principais Características

- Arquitetura de microsserviços desacoplados
- Comunicação assíncrona via message brokers
- Cache distribuído para otimização de performance
- Armazenamento híbrido (SQL, NoSQL, Object Storage)
- Sistema de recomendação baseado em machine learning
- Comunicação em tempo real via WebSocket
- Escalabilidade horizontal automática
- Observabilidade completa com métricas, logs e tracing

### Tecnologias Principais

- **Frontend**: React (Web), React Native (Mobile)
- **Backend**: Node.js (API Gateway, Messaging), Python (Recommendation Engine), Java (Content Service)
- **Bancos de Dados**: PostgreSQL (dados relacionais), MongoDB (dados não estruturados), Redis (cache)
- **Message Broker**: Apache Kafka
- **Busca**: Elasticsearch
- **Armazenamento**: AWS S3 ou compatível
- **Orquestração**: Kubernetes
- **Service Mesh**: Istio

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Load Balancer                            │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                        API Gateway                               │
│  - Autenticação JWT                                              │
│  - Rate Limiting                                                 │
│  - Request Routing                                               │
└─────┬──────────┬──────────┬──────────┬──────────┬──────────────┘
      │          │          │          │          │
      ▼          ▼          ▼          ▼          ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
│  User   │ │Content  │ │Messaging│ │  Search │ │  Notif  │
│ Service │ │ Service │ │ Service │ │ Service │ │ Service │
└────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘
     │           │           │           │           │
     └───────────┴───────────┴───────────┴───────────┘
                             │
                    ┌────────▼────────┐
                    │  Message Broker │
                    │     (Kafka)     │
                    └────────┬────────┘
                             │
     ┌───────────────────────┼───────────────────────┐
     │                       │                       │
     ▼                       ▼                       ▼
┌─────────┐          ┌─────────────┐         ┌──────────┐
│PostgreSQL│         │   MongoDB   │         │  Redis   │
└─────────┘          └─────────────┘         └──────────┘
                             │
                    ┌────────▼────────┐
                    │  Elasticsearch  │
                    └─────────────────┘
```

### Microsserviços

#### 1. API Gateway
- **Responsabilidade**: Ponto de entrada único, autenticação, rate limiting, roteamento
- **Tecnologia**: Node.js + Express + JWT
- **Padrões**: Circuit Breaker, Retry, Timeout
- **Escalabilidade**: Stateless, escala horizontalmente

#### 2. User Service
- **Responsabilidade**: Gerenciamento de usuários, perfis, autenticação, relacionamentos (seguir/seguidores)
- **Tecnologia**: Java + Spring Boot
- **Banco de Dados**: PostgreSQL (dados relacionais)
- **Cache**: Redis (perfis, contadores)

#### 3. Content Service
- **Responsabilidade**: Gerenciamento de posts, stories, interações (curtidas, comentários)
- **Tecnologia**: Java + Spring Boot
- **Banco de Dados**: MongoDB (posts, comentários), PostgreSQL (metadados)
- **Armazenamento**: S3 (imagens, vídeos)
- **Processamento**: Fila assíncrona para processamento de mídia

#### 4. Messaging Service
- **Responsabilidade**: Mensagens diretas em tempo real
- **Tecnologia**: Node.js + Socket.io
- **Banco de Dados**: MongoDB (histórico de mensagens)
- **Cache**: Redis (sessões WebSocket)
- **Protocolo**: WebSocket para comunicação bidirecional

#### 5. Notification Service
- **Responsabilidade**: Gerenciamento e envio de notificações push
- **Tecnologia**: Python + FastAPI
- **Banco de Dados**: MongoDB (histórico de notificações)
- **Integração**: Firebase Cloud Messaging (FCM), Apple Push Notification Service (APNs)

#### 6. Search Service
- **Responsabilidade**: Indexação e busca de conteúdo
- **Tecnologia**: Python + FastAPI
- **Banco de Dados**: Elasticsearch
- **Funcionalidades**: Busca full-text, fuzzy search, autocomplete

#### 7. Recommendation Engine
- **Responsabilidade**: Geração de feed personalizado e recomendações
- **Tecnologia**: Python + FastAPI + scikit-learn
- **Banco de Dados**: Redis (scores pré-computados), PostgreSQL (dados de treinamento)
- **Algoritmo**: Collaborative filtering + Content-based filtering

### Camada de Dados

#### PostgreSQL (Dados Relacionais)
- **Uso**: Usuários, relacionamentos, metadados de posts
- **Esquema**:
  - `users`: id, email, password_hash, name, bio, profile_picture_url, created_at
  - `followers`: follower_id, following_id, created_at
  - `post_metadata`: post_id, user_id, type, created_at, likes_count, comments_count

#### MongoDB (Dados Não Estruturados)
- **Uso**: Posts completos, comentários, mensagens, notificações
- **Collections**:
  - `posts`: {id, user_id, content, media_urls, hashtags, created_at}
  - `comments`: {id, post_id, user_id, content, created_at}
  - `messages`: {id, sender_id, receiver_id, content, media_url, created_at, read}
  - `notifications`: {id, user_id, type, content, read, created_at}

#### Redis (Cache)
- **Uso**: Feeds pré-computados, sessões, contadores, rate limiting
- **Estruturas**:
  - `feed:{user_id}`: Lista ordenada de post IDs
  - `user:{user_id}`: Hash com dados de perfil
  - `rate_limit:{user_id}`: Contador com TTL
  - `session:{token}`: Dados de sessão

#### Elasticsearch (Busca)
- **Uso**: Indexação de posts, usuários, hashtags
- **Índices**:
  - `posts`: Conteúdo de posts com análise de texto
  - `users`: Nomes e bios de usuários
  - `hashtags`: Agregação de hashtags populares

#### S3 (Armazenamento de Objetos)
- **Uso**: Imagens, vídeos, avatares
- **Estrutura**:
  - `media/posts/{user_id}/{post_id}/{filename}`
  - `media/stories/{user_id}/{story_id}/{filename}`
  - `media/avatars/{user_id}/{filename}`
- **CDN**: CloudFront para distribuição global

### Message Broker (Apache Kafka)

#### Topics

1. **user.events**
   - Eventos: user.created, user.updated, user.deleted
   - Consumidores: Search Service, Analytics Service

2. **content.events**
   - Eventos: post.created, post.deleted, comment.created, like.created
   - Consumidores: Notification Service, Search Service, Recommendation Engine

3. **social.events**
   - Eventos: follow.created, follow.deleted
   - Consumidores: Notification Service, Recommendation Engine

4. **notification.events**
   - Eventos: notification.created
   - Consumidores: Notification Service (para envio push)

#### Padrões de Mensageria

- **Event Sourcing**: Todos os eventos são armazenados para auditoria
- **CQRS**: Separação entre comandos (write) e queries (read)
- **Saga Pattern**: Transações distribuídas para operações complexas
- **Dead Letter Queue**: Tratamento de mensagens com falha

## Components and Interfaces

### API Gateway Endpoints

#### Authentication
```
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

#### Users
```
GET    /api/v1/users/{user_id}
PUT    /api/v1/users/{user_id}
DELETE /api/v1/users/{user_id}
GET    /api/v1/users/{user_id}/followers
GET    /api/v1/users/{user_id}/following
POST   /api/v1/users/{user_id}/follow
DELETE /api/v1/users/{user_id}/follow
```

#### Posts
```
POST   /api/v1/posts
GET    /api/v1/posts/{post_id}
DELETE /api/v1/posts/{post_id}
POST   /api/v1/posts/{post_id}/like
DELETE /api/v1/posts/{post_id}/like
POST   /api/v1/posts/{post_id}/comments
GET    /api/v1/posts/{post_id}/comments
```

#### Feed
```
GET /api/v1/feed
GET /api/v1/feed/trending
```

#### Stories
```
POST   /api/v1/stories
GET    /api/v1/stories/{user_id}
DELETE /api/v1/stories/{story_id}
GET    /api/v1/stories/{story_id}/viewers
```

#### Messages
```
WebSocket /api/v1/ws/messages
GET  /api/v1/messages/conversations
GET  /api/v1/messages/conversations/{user_id}
POST /api/v1/messages
```

#### Search
```
GET /api/v1/search?q={query}&type={posts|users|hashtags}
GET /api/v1/search/autocomplete?q={query}
```

#### Notifications
```
GET    /api/v1/notifications
PUT    /api/v1/notifications/{notification_id}/read
DELETE /api/v1/notifications/{notification_id}
```

### Inter-Service Communication

#### User Service Interface
```typescript
interface UserService {
  createUser(email: string, password: string, name: string): Promise<User>
  authenticateUser(email: string, password: string): Promise<AuthToken>
  getUserById(userId: string): Promise<User>
  updateUser(userId: string, updates: Partial<User>): Promise<User>
  deleteUser(userId: string): Promise<void>
  followUser(followerId: string, followingId: string): Promise<void>
  unfollowUser(followerId: string, followingId: string): Promise<void>
  getFollowers(userId: string, cursor?: string): Promise<PaginatedUsers>
  getFollowing(userId: string, cursor?: string): Promise<PaginatedUsers>
}
```

#### Content Service Interface
```typescript
interface ContentService {
  createPost(userId: string, content: string, mediaFiles?: File[]): Promise<Post>
  getPost(postId: string): Promise<Post>
  deletePost(postId: string): Promise<void>
  likePost(postId: string, userId: string): Promise<void>
  unlikePost(postId: string, userId: string): Promise<void>
  addComment(postId: string, userId: string, content: string): Promise<Comment>
  getComments(postId: string, cursor?: string): Promise<PaginatedComments>
  createStory(userId: string, mediaFile: File): Promise<Story>
  getStories(userId: string): Promise<Story[]>
  deleteStory(storyId: string): Promise<void>
  recordStoryView(storyId: string, viewerId: string): Promise<void>
}
```

#### Messaging Service Interface
```typescript
interface MessagingService {
  sendMessage(senderId: string, receiverId: string, content: string, mediaUrl?: string): Promise<Message>
  getConversation(userId1: string, userId2: string, cursor?: string): Promise<PaginatedMessages>
  getConversations(userId: string): Promise<Conversation[]>
  markAsRead(messageId: string): Promise<void>
  connectWebSocket(userId: string, token: string): Promise<WebSocketConnection>
}
```

#### Search Service Interface
```typescript
interface SearchService {
  indexPost(post: Post): Promise<void>
  indexUser(user: User): Promise<void>
  search(query: string, type: 'posts' | 'users' | 'hashtags', filters?: SearchFilters): Promise<SearchResults>
  autocomplete(query: string): Promise<string[]>
  getTrendingHashtags(limit: number): Promise<Hashtag[]>
}
```

#### Notification Service Interface
```typescript
interface NotificationService {
  createNotification(userId: string, type: NotificationType, content: any): Promise<Notification>
  sendPushNotification(userId: string, notification: Notification): Promise<void>
  getNotifications(userId: string, cursor?: string): Promise<PaginatedNotifications>
  markAsRead(notificationId: string): Promise<void>
  deleteNotification(notificationId: string): Promise<void>
}
```

#### Recommendation Engine Interface
```typescript
interface RecommendationEngine {
  generateFeed(userId: string, cursor?: string): Promise<Post[]>
  calculateRelevanceScore(userId: string, post: Post): Promise<number>
  updateUserPreferences(userId: string, interactions: Interaction[]): Promise<void>
  getTrendingPosts(limit: number): Promise<Post[]>
}
```

## Data Models

### User Model
```typescript
interface User {
  id: string;
  email: string;
  passwordHash: string;
  name: string;
  bio?: string;
  profilePictureUrl?: string;
  followersCount: number;
  followingCount: number;
  isPrivate: boolean;
  createdAt: Date;
  updatedAt: Date;
}
```

### Post Model
```typescript
interface Post {
  id: string;
  userId: string;
  content: string;
  mediaUrls: MediaUrl[];
  hashtags: string[];
  likesCount: number;
  commentsCount: number;
  sharesCount: number;
  createdAt: Date;
  updatedAt: Date;
}

interface MediaUrl {
  url: string;
  type: 'image' | 'video';
  thumbnailUrl?: string;
  width?: number;
  height?: number;
  duration?: number; // para vídeos
}
```

### Story Model
```typescript
interface Story {
  id: string;
  userId: string;
  mediaUrl: MediaUrl;
  viewsCount: number;
  expiresAt: Date;
  createdAt: Date;
}

interface StoryView {
  storyId: string;
  viewerId: string;
  viewedAt: Date;
}
```

### Comment Model
```typescript
interface Comment {
  id: string;
  postId: string;
  userId: string;
  content: string;
  likesCount: number;
  createdAt: Date;
  updatedAt: Date;
}
```

### Message Model
```typescript
interface Message {
  id: string;
  senderId: string;
  receiverId: string;
  content: string;
  mediaUrl?: string;
  isRead: boolean;
  deliveredAt?: Date;
  readAt?: Date;
  createdAt: Date;
}

interface Conversation {
  userId: string;
  lastMessage: Message;
  unreadCount: number;
}
```

### Notification Model
```typescript
interface Notification {
  id: string;
  userId: string;
  type: NotificationType;
  actorId: string; // usuário que gerou a notificação
  targetId: string; // post, comment, etc.
  content: string;
  isRead: boolean;
  createdAt: Date;
}

enum NotificationType {
  LIKE = 'like',
  COMMENT = 'comment',
  FOLLOW = 'follow',
  MENTION = 'mention',
  MESSAGE = 'message'
}
```

### Feed Item Model
```typescript
interface FeedItem {
  postId: string;
  userId: string;
  relevanceScore: number;
  timestamp: Date;
}
```

### Authentication Models
```typescript
interface AuthToken {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: 'Bearer';
}

interface JWTPayload {
  userId: string;
  email: string;
  iat: number;
  exp: number;
}
```

### Search Models
```typescript
interface SearchResults {
  posts?: Post[];
  users?: User[];
  hashtags?: Hashtag[];
  total: number;
  cursor?: string;
}

interface Hashtag {
  tag: string;
  postsCount: number;
  trending: boolean;
}
```

## Correctness Properties

*Uma propriedade é uma característica ou comportamento que deve ser verdadeiro em todas as execuções válidas de um sistema - essencialmente, uma declaração formal sobre o que o sistema deve fazer. Propriedades servem como ponte entre especificações legíveis por humanos e garantias de corretude verificáveis por máquina.*


### Gerenciamento de Usuários

Property 1: Registro de usuário válido cria conta
*Para qualquer* conjunto válido de dados de registro (email único, senha forte, nome), criar uma conta deve retornar um token de autenticação válido
**Validates: Requirements 1.1**

Property 2: Email duplicado rejeita registro
*Para qualquer* email já registrado no sistema, tentar criar nova conta com esse email deve resultar em rejeição com mensagem de erro descritiva
**Validates: Requirements 1.2**

Property 3: Autenticação com credenciais válidas retorna JWT
*Para qualquer* usuário registrado com credenciais corretas, autenticação deve retornar token JWT válido por 24 horas
**Validates: Requirements 1.3**

Property 4: Senhas são armazenadas com hash bcrypt
*Para qualquer* senha fornecida durante registro, o sistema nunca deve armazenar a senha em texto plano e deve usar bcrypt com mínimo 10 rounds
**Validates: Requirements 1.5**

Property 5: Atualização de perfil persiste alterações
*Para qualquer* atualização válida de perfil (nome, bio, foto), as alterações devem ser persistidas e refletidas em consultas subsequentes
**Validates: Requirements 1.6**

### Publicação de Conteúdo

Property 6: Post com texto válido é criado
*Para qualquer* texto entre 1 e 5000 caracteres, criar post deve retornar ID único e o post deve ser recuperável
**Validates: Requirements 2.1**

Property 7: Upload de imagem válida cria thumbnail
*Para qualquer* imagem válida (JPEG, PNG, WebP) menor que 10MB, o upload deve criar thumbnail e retornar URLs de imagem e thumbnail
**Validates: Requirements 2.2**

Property 8: Upload de vídeo válido processa múltiplas resoluções
*Para qualquer* vídeo válido (MP4, WebM) menor que 100MB, o upload deve processar e disponibilizar em resoluções 480p, 720p e 1080p
**Validates: Requirements 2.3**

Property 9: Arquivo excedendo tamanho máximo é rejeitado
*Para qualquer* arquivo que excede limites (>10MB para imagem, >100MB para vídeo), o upload deve ser rejeitado com mensagem de erro específica
**Validates: Requirements 2.4**

Property 10: Hashtags são extraídas automaticamente
*Para qualquer* post contendo padrão #palavra, todas as hashtags devem ser extraídas e indexadas automaticamente
**Validates: Requirements 2.5**

Property 11: Criação de post publica evento
*Para qualquer* post criado, um evento deve ser publicado no message broker para processamento assíncrono
**Validates: Requirements 2.6**

Property 12: Exclusão de post remove do feed
*Para qualquer* post existente, solicitar exclusão deve marcar como deletado e remover de todos os feeds
**Validates: Requirements 2.7**

### Sistema de Stories

Property 13: Story criado tem expiração de 24 horas
*Para qualquer* story publicado, o timestamp de expiração deve ser exatamente 24 horas após criação
**Validates: Requirements 3.1**

Property 14: Visualização retorna apenas stories não expirados
*Para qualquer* conjunto de stories (expirados e não expirados), a consulta deve retornar apenas stories não expirados ordenados por timestamp
**Validates: Requirements 3.3**

Property 15: Visualizações de stories são registradas
*Para qualquer* visualização de story, o sistema deve registrar ID do visualizador e timestamp
**Validates: Requirements 3.4**

Property 16: Lista de visualizadores filtra por 24 horas
*Para qualquer* story, a lista de visualizadores deve incluir apenas usuários que visualizaram nos últimos 24 horas
**Validates: Requirements 3.5**

### Feed Personalizado

Property 17: Feed contém apenas posts de seguidos
*Para qualquer* usuário, o feed deve conter apenas posts de usuários que ele segue
**Validates: Requirements 4.1**

Property 18: Score de relevância reflete engajamento
*Para quaisquer* dois posts, o post com maior engajamento (curtidas + comentários) deve ter score de relevância maior ou igual
**Validates: Requirements 4.2**

Property 19: Novo post invalida cache de seguidores
*Para qualquer* post criado, o cache do feed de todos os seguidores do autor deve ser invalidado
**Validates: Requirements 4.4**

Property 20: Paginação retorna 20 posts por página
*Para qualquer* requisição de feed, cada página deve conter exatamente 20 posts (ou menos na última página)
**Validates: Requirements 4.5**

### Sistema de Seguir/Seguidores

Property 21: Seguir usuário cria relacionamento
*Para quaisquer* dois usuários distintos A e B, A seguir B deve criar relacionamento e incrementar contadores
**Validates: Requirements 5.1**

Property 22: Deixar de seguir remove relacionamento
*Para qualquer* relacionamento existente, deixar de seguir deve remover o relacionamento e decrementar contadores corretamente
**Validates: Requirements 5.3**

Property 23: Contadores refletem relacionamentos reais
*Para qualquer* usuário, os contadores de seguidores e seguindo devem sempre corresponder ao número real de relacionamentos no banco
**Validates: Requirements 5.4**

Property 24: Lista de seguidores paginada com 50 por página
*Para qualquer* requisição de lista de seguidores, cada página deve conter exatamente 50 usuários (ou menos na última página)
**Validates: Requirements 5.5**

### Interações Sociais

Property 25: Curtir post incrementa contador
*Para qualquer* post, curtir deve incrementar o contador de curtidas em exatamente 1
**Validates: Requirements 6.1**

Property 26: Descurtir post decrementa contador
*Para qualquer* post curtido, remover curtida deve decrementar o contador em exatamente 1
**Validates: Requirements 6.2**

Property 27: Curtir é idempotente
*Para qualquer* post, curtir múltiplas vezes deve ter o mesmo efeito que curtir uma vez (contador incrementa apenas 1)
**Validates: Requirements 6.3**

Property 28: Comentário válido é criado
*Para qualquer* comentário entre 1 e 1000 caracteres, criar comentário deve associá-lo ao post e incrementar contador de comentários
**Validates: Requirements 6.4**

Property 29: Compartilhar cria post com referência
*Para qualquer* post compartilhado, um novo post deve ser criado contendo referência ao post original
**Validates: Requirements 6.5**

Property 30: Interações publicam eventos
*Para qualquer* interação (curtida, comentário, compartilhamento), um evento deve ser publicado no message broker
**Validates: Requirements 6.6**

Property 31: Interação atualiza score de relevância
*Para qualquer* post que recebe interação, o score de relevância deve ser recalculado e atualizado
**Validates: Requirements 6.7**

### Mensagens Diretas

Property 32: Mensagem válida é entregue
*Para qualquer* mensagem de texto entre 1 e 5000 caracteres, enviar deve resultar em entrega ao destinatário
**Validates: Requirements 7.1**

Property 33: Mensagem para usuário offline é armazenada
*Para qualquer* mensagem enviada a usuário offline, a mensagem deve ser armazenada e entregue quando usuário conectar
**Validates: Requirements 7.2**

Property 34: Imagem em mensagem gera URL
*Para qualquer* imagem enviada em mensagem, o sistema deve fazer upload e retornar URL acessível
**Validates: Requirements 7.4**

Property 35: Histórico paginado com 50 mensagens
*Para qualquer* requisição de histórico, cada página deve conter exatamente 50 mensagens (ou menos na última página)
**Validates: Requirements 7.5**

Property 36: Entrega envia confirmação
*Para qualquer* mensagem entregue, uma confirmação de entrega deve ser enviada ao remetente
**Validates: Requirements 7.6**

Property 37: Leitura envia confirmação
*Para qualquer* mensagem lida, uma confirmação de leitura deve ser enviada ao remetente
**Validates: Requirements 7.7**

### Sistema de Notificações

Property 38: Curtida cria notificação
*Para qualquer* curtida em post, uma notificação deve ser criada para o autor do post
**Validates: Requirements 8.1**

Property 39: Múltiplos comentários são agregados
*Para quaisquer* múltiplos comentários no mesmo post dentro de 5 minutos, uma única notificação agregada deve ser criada
**Validates: Requirements 8.2**

Property 40: Novo seguidor cria notificação
*Para qualquer* novo relacionamento de seguidor, uma notificação deve ser criada para o usuário seguido
**Validates: Requirements 8.3**

Property 41: Preferências de notificação são respeitadas
*Para qualquer* usuário com preferências configuradas, notificações devem ser enviadas apenas para tipos habilitados
**Validates: Requirements 8.4**

Property 42: Push notification é enviado quando habilitado
*Para qualquer* notificação criada quando push está habilitado, uma chamada deve ser feita para FCM ou APNs
**Validates: Requirements 8.5**

Property 43: Visualizar notificação marca como lida
*Para qualquer* notificação não lida, visualizar deve marcar como lida
**Validates: Requirements 8.7**

### Busca e Descoberta

Property 44: Busca retorna resultados relevantes
*Para qualquer* termo de busca com mínimo 2 caracteres, resultados devem conter apenas itens que correspondem ao termo
**Validates: Requirements 9.1**

Property 45: Novo post é indexado
*Para qualquer* post criado, o conteúdo deve ser indexado e aparecer em buscas subsequentes
**Validates: Requirements 9.2, 9.3**

Property 46: Busca fuzzy tolera erros
*Para qualquer* termo com erro de digitação (1-2 caracteres), busca fuzzy deve retornar resultados do termo correto
**Validates: Requirements 9.4**

Property 47: Busca por hashtag ordena corretamente
*Para qualquer* hashtag buscada, posts devem ser ordenados por combinação de recência e popularidade
**Validates: Requirements 9.5**

Property 48: Filtros retornam apenas tipo especificado
*Para qualquer* busca com filtro de tipo (posts, usuários, hashtags), resultados devem conter apenas itens do tipo especificado
**Validates: Requirements 9.6**

Property 49: Autocomplete retorna sugestões
*Para qualquer* prefixo de busca, autocomplete deve retornar lista de sugestões relevantes
**Validates: Requirements 9.7**

### Segurança e Rate Limiting

Property 50: Rate limit bloqueia após 100 requisições
*Para qualquer* usuário autenticado, fazer mais de 100 requisições por minuto deve resultar em bloqueio com erro 429
**Validates: Requirements 10.1**

Property 51: Exceder rate limit retorna 429 com Retry-After
*Para qualquer* usuário que excede rate limit, a resposta deve ser 429 com header Retry-After indicando quando tentar novamente
**Validates: Requirements 10.2**

Property 52: Entradas são sanitizadas
*Para qualquer* entrada de usuário contendo código malicioso (SQL injection, XSS), o sistema deve sanitizar antes de processar
**Validates: Requirements 10.4**

Property 53: Acessos suspeitos são registrados
*Para qualquer* tentativa de acesso suspeita (múltiplas falhas de login, padrões anormais), um registro deve ser criado no log de auditoria
**Validates: Requirements 10.6**

### Conformidade e Privacidade

Property 54: Exportação de dados retorna JSON completo
*Para qualquer* usuário, exportar dados deve retornar arquivo JSON contendo todos os dados pessoais e conteúdo
**Validates: Requirements 14.1**

Property 55: Exclusão remove dados pessoais
*Para qualquer* usuário que solicita exclusão, todos os dados pessoais devem ser removidos conforme LGPD
**Validates: Requirements 14.2**

Property 56: Dados pessoais são criptografados
*Para qualquer* dado pessoal armazenado, o sistema deve usar criptografia AES-256 em repouso
**Validates: Requirements 14.4**

Property 57: Controle de acesso baseado em roles
*Para qualquer* dado sensível, apenas usuários com role autorizado devem ter acesso
**Validates: Requirements 14.5**

Property 58: Acessos a dados pessoais são auditados
*Para qualquer* acesso a dados pessoais, um registro deve ser criado no log de auditoria
**Validates: Requirements 14.6**

Property 59: Privacidade de perfil é respeitada
*Para qualquer* perfil configurado como privado, apenas seguidores aprovados devem visualizar conteúdo
**Validates: Requirements 14.7**

### Integração e APIs

Property 60: Rate limiting diferenciado por tier
*Para quaisquer* dois tiers diferentes (free, premium), o tier premium deve ter limite de requisições maior ou igual ao free
**Validates: Requirements 15.5**

Property 61: Webhooks são chamados para eventos
*Para qualquer* evento importante (novo post, nova mensagem), webhooks configurados devem ser chamados com payload correto
**Validates: Requirements 15.6**

Property 62: Erros retornam formato padronizado
*Para qualquer* erro da API, a resposta deve seguir formato padronizado com código de erro e mensagem descritiva
**Validates: Requirements 15.7**

## Error Handling

### Estratégia Geral

O sistema implementa tratamento de erros em múltiplas camadas:

1. **Validação de Entrada**: Todos os dados de entrada são validados antes do processamento
2. **Erros de Negócio**: Regras de negócio violadas retornam erros específicos
3. **Erros de Sistema**: Falhas técnicas são tratadas com retry e circuit breaker
4. **Erros de Integração**: Falhas em serviços externos são isoladas

### Códigos de Erro HTTP

```typescript
enum ErrorCode {
  // 4xx - Erros do Cliente
  BAD_REQUEST = 400,           // Dados inválidos
  UNAUTHORIZED = 401,          // Não autenticado
  FORBIDDEN = 403,             // Não autorizado
  NOT_FOUND = 404,             // Recurso não encontrado
  CONFLICT = 409,              // Conflito (ex: email duplicado)
  UNPROCESSABLE_ENTITY = 422,  // Validação falhou
  TOO_MANY_REQUESTS = 429,     // Rate limit excedido
  
  // 5xx - Erros do Servidor
  INTERNAL_SERVER_ERROR = 500, // Erro interno
  SERVICE_UNAVAILABLE = 503,   // Serviço temporariamente indisponível
  GATEWAY_TIMEOUT = 504        // Timeout em serviço downstream
}
```

### Formato de Resposta de Erro

```typescript
interface ErrorResponse {
  error: {
    code: string;           // Código de erro legível (ex: "EMAIL_ALREADY_EXISTS")
    message: string;        // Mensagem descritiva para o usuário
    details?: any;          // Detalhes adicionais (ex: campos inválidos)
    requestId: string;      // ID para rastreamento
    timestamp: string;      // Timestamp ISO 8601
  }
}
```

### Tratamento por Microsserviço

#### API Gateway
- **Timeout**: 30 segundos para requisições downstream
- **Circuit Breaker**: Abre após 5 falhas consecutivas, fecha após 30 segundos
- **Retry**: Até 3 tentativas com backoff exponencial para erros 5xx
- **Fallback**: Retorna resposta em cache quando disponível

#### User Service
- **Validação**: Email, senha forte, campos obrigatórios
- **Erros Comuns**:
  - `EMAIL_ALREADY_EXISTS`: Email já registrado
  - `INVALID_CREDENTIALS`: Credenciais inválidas
  - `USER_NOT_FOUND`: Usuário não existe
  - `CANNOT_FOLLOW_SELF`: Tentativa de seguir a si mesmo

#### Content Service
- **Validação**: Tamanho de arquivo, formato, comprimento de texto
- **Erros Comuns**:
  - `FILE_TOO_LARGE`: Arquivo excede tamanho máximo
  - `INVALID_FILE_FORMAT`: Formato não suportado
  - `POST_NOT_FOUND`: Post não existe
  - `ALREADY_LIKED`: Tentativa de curtir post já curtido (tratado como idempotente)

#### Messaging Service
- **Validação**: Comprimento de mensagem, destinatário válido
- **Erros Comuns**:
  - `RECIPIENT_NOT_FOUND`: Destinatário não existe
  - `MESSAGE_TOO_LONG`: Mensagem excede limite
  - `WEBSOCKET_CONNECTION_FAILED`: Falha na conexão WebSocket

#### Search Service
- **Validação**: Termo de busca mínimo 2 caracteres
- **Erros Comuns**:
  - `QUERY_TOO_SHORT`: Termo de busca muito curto
  - `SEARCH_TIMEOUT`: Busca excedeu timeout
  - `INDEX_UNAVAILABLE`: Elasticsearch indisponível

#### Notification Service
- **Validação**: Tipo de notificação válido, usuário existe
- **Erros Comuns**:
  - `PUSH_NOTIFICATION_FAILED`: Falha ao enviar push (não bloqueia operação)
  - `NOTIFICATION_NOT_FOUND`: Notificação não existe

### Tratamento de Falhas em Message Broker

- **Dead Letter Queue**: Mensagens com falha após 3 tentativas vão para DLQ
- **Retry Policy**: Backoff exponencial (1s, 2s, 4s)
- **Monitoring**: Alertas quando DLQ atinge threshold

### Logging e Monitoramento de Erros

- **Structured Logging**: Todos os erros são logados com contexto
- **Error Tracking**: Integração com Sentry ou similar
- **Alerting**: Alertas quando taxa de erro > 5%
- **Distributed Tracing**: Rastreamento de erros através de microsserviços

## Testing Strategy

### Abordagem Dual de Testes

O sistema utiliza uma estratégia complementar de testes:

1. **Testes Unitários**: Validam exemplos específicos, casos de borda e condições de erro
2. **Testes Baseados em Propriedades**: Validam propriedades universais através de múltiplas entradas geradas

Ambos são necessários para cobertura abrangente - testes unitários capturam bugs concretos, testes de propriedade verificam corretude geral.

### Property-Based Testing

#### Configuração

- **Biblioteca**: fast-check (JavaScript/TypeScript), Hypothesis (Python), jqwik (Java)
- **Iterações Mínimas**: 100 por teste de propriedade
- **Seed**: Configurável para reproduzir falhas
- **Shrinking**: Automático para encontrar caso mínimo de falha

#### Formato de Tag

Cada teste de propriedade deve referenciar a propriedade do design:

```typescript
// Feature: rede-social-brasileira, Property 1: Registro de usuário válido cria conta
test('user registration with valid data returns auth token', async () => {
  await fc.assert(
    fc.asyncProperty(
      fc.emailAddress(),
      fc.string({ minLength: 8, maxLength: 50 }),
      fc.string({ minLength: 2, maxLength: 100 }),
      async (email, password, name) => {
        const result = await userService.createUser(email, password, name);
        expect(result.accessToken).toBeDefined();
        expect(result.userId).toBeDefined();
      }
    ),
    { numRuns: 100 }
  );
});
```

#### Geradores Customizados

```typescript
// Gerador de posts válidos
const validPost = fc.record({
  content: fc.string({ minLength: 1, maxLength: 5000 }),
  mediaUrls: fc.array(fc.webUrl(), { maxLength: 10 }),
  hashtags: fc.array(fc.string({ minLength: 2, maxLength: 30 }), { maxLength: 30 })
});

// Gerador de usuários válidos
const validUser = fc.record({
  email: fc.emailAddress(),
  password: fc.string({ minLength: 8, maxLength: 50 }),
  name: fc.string({ minLength: 2, maxLength: 100 }),
  bio: fc.option(fc.string({ maxLength: 500 }))
});
```

### Testes Unitários

#### Foco

- Exemplos específicos que demonstram comportamento correto
- Casos de borda (limites de tamanho, valores nulos, listas vazias)
- Condições de erro (validação, autenticação, autorização)
- Pontos de integração entre componentes

#### Evitar Excesso

- Não escrever muitos testes unitários para cobrir múltiplas entradas
- Testes de propriedade já cobrem variação de inputs
- Focar em casos específicos que demonstram valor único

#### Exemplos

```typescript
// Caso de borda: tentativa de seguir a si mesmo
test('user cannot follow themselves', async () => {
  const userId = 'user-123';
  await expect(
    userService.followUser(userId, userId)
  ).rejects.toThrow('CANNOT_FOLLOW_SELF');
});

// Exemplo específico: token expirado
test('expired JWT token is rejected', async () => {
  const expiredToken = generateExpiredToken();
  await expect(
    apiGateway.authenticate(expiredToken)
  ).rejects.toThrow('TOKEN_EXPIRED');
});

// Condição de erro: arquivo muito grande
test('image larger than 10MB is rejected', async () => {
  const largeImage = generateImage(11 * 1024 * 1024); // 11MB
  await expect(
    contentService.uploadImage(largeImage)
  ).rejects.toThrow('FILE_TOO_LARGE');
});
```

### Testes de Integração

#### Escopo

- Comunicação entre microsserviços
- Fluxos end-to-end críticos
- Integração com bancos de dados
- Integração com message broker

#### Ambiente

- Containers Docker para dependências (PostgreSQL, MongoDB, Redis, Kafka)
- Testcontainers para setup automático
- Dados de teste isolados por execução

#### Exemplos

```typescript
// Fluxo completo: criar post e verificar notificação
test('creating post notifies followers', async () => {
  const author = await createTestUser();
  const follower = await createTestUser();
  await userService.followUser(follower.id, author.id);
  
  const post = await contentService.createPost(author.id, 'Test post');
  
  // Aguardar processamento assíncrono
  await waitFor(async () => {
    const notifications = await notificationService.getNotifications(follower.id);
    expect(notifications).toContainEqual(
      expect.objectContaining({
        type: 'NEW_POST',
        actorId: author.id,
        targetId: post.id
      })
    );
  });
});
```

### Testes de Performance

#### Métricas

- Latência P50, P95, P99
- Throughput (requisições por segundo)
- Taxa de erro
- Utilização de recursos

#### Ferramentas

- k6 para testes de carga
- Artillery para testes de stress
- Grafana para visualização

#### Cenários

```javascript
// Teste de carga: feed personalizado
export default function() {
  const token = authenticate();
  
  http.get('https://api.example.com/api/v1/feed', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  
  sleep(1);
}

// Thresholds
export let options = {
  thresholds: {
    http_req_duration: ['p(95)<200'], // 95% das requisições < 200ms
    http_req_failed: ['rate<0.01'],   // Taxa de erro < 1%
  }
};
```

### Cobertura de Testes

#### Metas

- Cobertura de código: mínimo 80%
- Cobertura de propriedades: 100% das propriedades testadas
- Cobertura de requisitos: 100% dos requisitos testáveis

#### Ferramentas

- Istanbul/nyc para cobertura de código JavaScript/TypeScript
- JaCoCo para cobertura de código Java
- Coverage.py para cobertura de código Python

### CI/CD Pipeline

#### Estágios

1. **Lint**: Verificação de estilo de código
2. **Unit Tests**: Testes unitários e de propriedade
3. **Integration Tests**: Testes de integração
4. **Build**: Construção de imagens Docker
5. **Deploy to Staging**: Deploy em ambiente de staging
6. **E2E Tests**: Testes end-to-end em staging
7. **Deploy to Production**: Deploy em produção (manual)

#### Qualidade Gates

- Todos os testes devem passar
- Cobertura de código > 80%
- Sem vulnerabilidades críticas (Snyk, Dependabot)
- Performance dentro dos thresholds

### Testes de Segurança

#### SAST (Static Application Security Testing)

- SonarQube para análise de código
- Verificação de dependências vulneráveis
- Detecção de secrets em código

#### DAST (Dynamic Application Security Testing)

- OWASP ZAP para testes de penetração
- Testes de injeção SQL, XSS, CSRF
- Testes de autenticação e autorização

#### Testes de Rate Limiting

```typescript
test('rate limiting blocks after 100 requests', async () => {
  const token = await authenticate();
  
  // Fazer 100 requisições
  for (let i = 0; i < 100; i++) {
    await apiGateway.get('/api/v1/feed', { headers: { Authorization: `Bearer ${token}` } });
  }
  
  // 101ª requisição deve ser bloqueada
  await expect(
    apiGateway.get('/api/v1/feed', { headers: { Authorization: `Bearer ${token}` } })
  ).rejects.toMatchObject({
    status: 429,
    headers: expect.objectContaining({
      'retry-after': expect.any(String)
    })
  });
});
```

### Monitoramento de Testes

- **Test Analytics**: Rastreamento de flakiness e duração
- **Failure Tracking**: Análise de falhas recorrentes
- **Trend Analysis**: Tendências de cobertura e performance ao longo do tempo
