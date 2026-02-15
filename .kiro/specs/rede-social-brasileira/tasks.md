# Implementation Plan: Rede Social Brasileira

## Overview

Este plano implementa uma rede social brasileira moderna com arquitetura de microsserviços. A implementação será incremental, começando pela infraestrutura base, seguida pelos microsserviços core, e finalizando com funcionalidades avançadas. Cada tarefa constrói sobre as anteriores, garantindo que o código seja integrado progressivamente.

## Tasks

- [x] 1. Setup de Infraestrutura e Configuração Base
  - Criar estrutura de diretórios para microsserviços
  - Configurar Docker Compose para desenvolvimento local (PostgreSQL, MongoDB, Redis, Kafka, Elasticsearch)
  - Configurar variáveis de ambiente e arquivos de configuração
  - Setup de logging estruturado e distributed tracing
  - _Requirements: 11.4, 12.2, 12.3_

- [x] 2. Implementar API Gateway (Node.js)
  - [x] 2.1 Criar servidor Express com roteamento básico
    - Implementar endpoints de health check
    - Configurar CORS e middleware de segurança
    - _Requirements: 12.5, 10.5_
  
  - [x] 2.2 Implementar autenticação JWT
    - Criar middleware de autenticação
    - Implementar geração e validação de tokens JWT
    - Configurar refresh tokens
    - _Requirements: 1.3, 1.4_
  
  - [x] 2.3 Escrever testes de propriedade para autenticação
    - **Property 3: Autenticação com credenciais válidas retorna JWT**
    - **Validates: Requirements 1.3**
  
  - [x] 2.4 Implementar rate limiting
    - Configurar Redis para armazenar contadores
    - Implementar middleware de rate limiting (100 req/min geral, 10 req/min auth)
    - Retornar erro 429 com header Retry-After
    - _Requirements: 10.1, 10.2, 10.3_
  
  - [x] 2.5 Escrever testes de propriedade para rate limiting
    - **Property 50: Rate limit bloqueia após 100 requisições**
    - **Property 51: Exceder rate limit retorna 429 com Retry-After**
    - **Validates: Requirements 10.1, 10.2**
  
  - [x] 2.6 Implementar circuit breaker e retry logic
    - Configurar circuit breaker para chamadas downstream
    - Implementar retry com backoff exponencial
    - _Requirements: 11.7_


- [x] 3. Implementar User Service (Java + Spring Boot)
  - [x] 3.1 Criar projeto Spring Boot com dependências
    - Configurar Spring Data JPA para PostgreSQL
    - Configurar Redis para cache
    - Setup de migrations com Flyway
    - _Requirements: 1.1_
  
  - [x] 3.2 Implementar modelos de dados de usuário
    - Criar entidade User com validações
    - Criar entidade Follower para relacionamentos
    - Implementar repositories JPA
    - _Requirements: 1.1, 5.1_
  
  - [x] 3.3 Implementar registro e autenticação de usuários
    - Criar endpoint de registro com validação de email único
    - Implementar hash de senha com bcrypt (min 10 rounds)
    - Criar endpoint de login
    - _Requirements: 1.1, 1.2, 1.3, 1.5_
  
  - [x] 3.4 Escrever testes de propriedade para registro
    - **Property 1: Registro de usuário válido cria conta**
    - **Property 2: Email duplicado rejeita registro**
    - **Property 4: Senhas são armazenadas com hash bcrypt**
    - **Validates: Requirements 1.1, 1.2, 1.5**
  
  - [x] 3.5 Implementar gerenciamento de perfil
    - Criar endpoints para atualizar perfil (nome, bio, foto)
    - Implementar upload de avatar para S3
    - Criar endpoint para exclusão de conta
    - _Requirements: 1.6, 1.7_
  
  - [x] 3.6 Escrever testes de propriedade para perfil
    - **Property 5: Atualização de perfil persiste alterações**
    - **Validates: Requirements 1.6**
  
  - [x] 3.7 Implementar sistema de seguir/seguidores
    - Criar endpoints para seguir e deixar de seguir
    - Implementar validação (não pode seguir a si mesmo)
    - Manter contadores denormalizados (followers_count, following_count)
    - Criar endpoints para listar seguidores e seguindo com paginação (50 por página)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_
  
  - [x] 3.8 Escrever testes de propriedade para relacionamentos
    - **Property 21: Seguir usuário cria relacionamento**
    - **Property 22: Deixar de seguir remove relacionamento**
    - **Property 23: Contadores refletem relacionamentos reais**
    - **Property 24: Lista de seguidores paginada com 50 por página**
    - **Validates: Requirements 5.1, 5.3, 5.4, 5.5**
  
  - [x] 3.9 Escrever testes unitários para casos de borda
    - Testar tentativa de seguir a si mesmo
    - Testar email duplicado
    - Testar validações de campos obrigatórios

- [x] 4. Checkpoint - Verificar User Service
  - Garantir que todos os testes passam
  - Verificar integração com PostgreSQL e Redis
  - Testar endpoints via API Gateway
  - Perguntar ao usuário se há dúvidas

- [x] 5. Implementar Content Service (Java + Spring Boot)
  - [x] 5.1 Criar projeto Spring Boot com dependências
    - Configurar MongoDB para posts e comentários
    - Configurar PostgreSQL para metadados
    - Configurar S3 para armazenamento de mídia
    - _Requirements: 2.1_
  
  - [x] 5.2 Implementar modelos de dados de conteúdo
    - Criar documento Post no MongoDB
    - Criar entidade PostMetadata no PostgreSQL
    - Criar documento Comment no MongoDB
    - Criar documento Story no MongoDB
    - _Requirements: 2.1, 3.1_
  
  - [x] 5.3 Implementar criação de posts
    - Criar endpoint para criar post com texto (1-5000 caracteres)
    - Implementar validação de tamanho de texto
    - Extrair e indexar hashtags automaticamente (#palavra)
    - Publicar evento post.created no Kafka
    - _Requirements: 2.1, 2.5, 2.6_
  
  - [x] 5.4 Escrever testes de propriedade para posts
    - **Property 6: Post com texto válido é criado**
    - **Property 10: Hashtags são extraídas automaticamente**
    - **Property 11: Criação de post publica evento**
    - **Validates: Requirements 2.1, 2.5, 2.6**
  
  - [x] 5.5 Implementar upload de mídia
    - Criar endpoint para upload de imagens (JPEG, PNG, WebP < 10MB)
    - Implementar geração de thumbnails
    - Criar endpoint para upload de vídeos (MP4, WebM < 100MB)
    - Implementar processamento de vídeo em múltiplas resoluções (480p, 720p, 1080p)
    - Validar tamanho e formato de arquivos
    - _Requirements: 2.2, 2.3, 2.4_
  
  - [x] 5.6 Escrever testes de propriedade para upload
    - **Property 7: Upload de imagem válida cria thumbnail**
    - **Property 8: Upload de vídeo válido processa múltiplas resoluções**
    - **Property 9: Arquivo excedendo tamanho máximo é rejeitado**
    - **Validates: Requirements 2.2, 2.3, 2.4**
  
  - [x] 5.7 Implementar exclusão de posts
    - Criar endpoint para deletar post
    - Marcar como deletado (soft delete)
    - Publicar evento post.deleted no Kafka
    - _Requirements: 2.7_
  
  - [x] 5.8 Escrever testes de propriedade para exclusão
    - **Property 12: Exclusão de post remove do feed**
    - **Validates: Requirements 2.7**
  
  - [x] 5.9 Implementar sistema de stories
    - Criar endpoint para publicar story com imagem/vídeo
    - Configurar timestamp de expiração (24 horas)
    - Implementar job para remover stories expirados
    - Criar endpoint para visualizar stories (apenas não expirados)
    - Registrar visualizações com user_id e timestamp
    - Criar endpoint para listar visualizadores (últimas 24h)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
  
  - [x] 5.10 Escrever testes de propriedade para stories
    - **Property 13: Story criado tem expiração de 24 horas**
    - **Property 14: Visualização retorna apenas stories não expirados**
    - **Property 15: Visualizações de stories são registradas**
    - **Property 16: Lista de visualizadores filtra por 24 horas**
    - **Validates: Requirements 3.1, 3.3, 3.4, 3.5**

- [x] 6. Implementar Interações Sociais no Content Service
  - [x] 6.1 Implementar sistema de curtidas
    - Criar endpoint para curtir post
    - Incrementar contador likes_count
    - Criar endpoint para descurtir post
    - Decrementar contador likes_count
    - Garantir idempotência (curtir múltiplas vezes = curtir uma vez)
    - Publicar evento like.created no Kafka
    - _Requirements: 6.1, 6.2, 6.3, 6.6_
  
  - [x] 6.2 Escrever testes de propriedade para curtidas
    - **Property 25: Curtir post incrementa contador**
    - **Property 26: Descurtir post decrementa contador**
    - **Property 27: Curtir é idempotente**
    - **Property 30: Interações publicam eventos**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.6**
  
  - [x] 6.3 Implementar sistema de comentários
    - Criar endpoint para adicionar comentário (1-1000 caracteres)
    - Incrementar contador comments_count do post
    - Criar endpoint para listar comentários com paginação
    - Publicar evento comment.created no Kafka
    - _Requirements: 6.4, 6.6_
  
  - [x] 6.4 Escrever testes de propriedade para comentários
    - **Property 28: Comentário válido é criado**
    - **Validates: Requirements 6.4**
  
  - [x] 6.5 Implementar compartilhamento de posts
    - Criar endpoint para compartilhar post
    - Criar novo post referenciando o original
    - Incrementar contador shares_count
    - Publicar evento share.created no Kafka
    - _Requirements: 6.5, 6.6_
  
  - [x] 6.6 Escrever testes de propriedade para compartilhamento
    - **Property 29: Compartilhar cria post com referência**
    - **Validates: Requirements 6.5**

- [x] 7. Checkpoint - Verificar Content Service
  - Garantir que todos os testes passam
  - Verificar integração com MongoDB, PostgreSQL e S3
  - Testar upload e processamento de mídia
  - Verificar publicação de eventos no Kafka
  - Perguntar ao usuário se há dúvidas

- [x] 8. Implementar Recommendation Engine (Python + FastAPI)
  - [x] 8.1 Criar projeto FastAPI com dependências
    - Configurar conexão com PostgreSQL para dados de treinamento
    - Configurar Redis para scores pré-computados
    - Setup de scikit-learn para algoritmos de recomendação
    - _Requirements: 4.2_
  
  - [x] 8.2 Implementar cálculo de score de relevância
    - Criar função para calcular score baseado em engajamento (curtidas, comentários)
    - Considerar timestamp para decaimento temporal
    - Armazenar scores no Redis com TTL
    - _Requirements: 4.2_
  
  - [x] 8.3 Escrever testes de propriedade para scores
    - **Property 18: Score de relevância reflete engajamento**
    - **Validates: Requirements 4.2**
  
  - [x] 8.4 Implementar geração de feed personalizado
    - Criar endpoint para gerar feed de usuário
    - Buscar posts de usuários seguidos
    - Ordenar por score de relevância e timestamp
    - Implementar paginação cursor-based (20 posts por página)
    - Cachear feeds no Redis com TTL de 5 minutos
    - _Requirements: 4.1, 4.3, 4.5_
  
  - [x] 8.5 Escrever testes de propriedade para feed
    - **Property 17: Feed contém apenas posts de seguidos**
    - **Property 20: Paginação retorna 20 posts por página**
    - **Validates: Requirements 4.1, 4.5**
  
  - [x] 8.6 Implementar invalidação de cache
    - Consumir eventos post.created do Kafka
    - Invalidar cache do feed dos seguidores do autor
    - _Requirements: 4.4_
  
  - [x] 8.7 Escrever testes de propriedade para invalidação
    - **Property 19: Novo post invalida cache de seguidores**
    - **Validates: Requirements 4.4**
  
  - [x] 8.8 Implementar feed para usuários sem seguidos
    - Retornar posts populares baseados em trending topics
    - Usar score de relevância global
    - _Requirements: 4.6_
  
  - [x] 8.9 Implementar atualização de scores por interações
    - Consumir eventos de interação (like, comment, share) do Kafka
    - Recalcular e atualizar scores de relevância
    - _Requirements: 6.7_
  
  - [x] 8.10 Escrever testes de propriedade para atualização
    - **Property 31: Interação atualiza score de relevância**
    - **Validates: Requirements 6.7**

- [x] 9. Implementar Search Service (Python + FastAPI)
  - [x] 9.1 Criar projeto FastAPI com dependências
    - Configurar cliente Elasticsearch
    - Criar índices para posts, users e hashtags
    - _Requirements: 9.2_
  
  - [x] 9.2 Implementar indexação de conteúdo
    - Consumir eventos do Kafka (post.created, user.created)
    - Indexar posts no Elasticsearch
    - Indexar usuários no Elasticsearch
    - Extrair e indexar hashtags
    - _Requirements: 9.2, 9.3_
  
  - [x] 9.3 Escrever testes de propriedade para indexação
    - **Property 45: Novo post é indexado**
    - **Validates: Requirements 9.2, 9.3**
  
  - [x] 9.4 Implementar busca com fuzzy matching
    - Criar endpoint de busca (mínimo 2 caracteres)
    - Implementar busca fuzzy para tolerar erros de digitação
    - Suportar filtros por tipo (posts, users, hashtags)
    - Retornar resultados em menos de 500ms
    - _Requirements: 9.1, 9.4, 9.6_
  
  - [x] 9.5 Escrever testes de propriedade para busca
    - **Property 44: Busca retorna resultados relevantes**
    - **Property 46: Busca fuzzy tolera erros**
    - **Property 48: Filtros retornam apenas tipo especificado**
    - **Validates: Requirements 9.1, 9.4, 9.6**
  
  - [x] 9.6 Implementar busca por hashtag
    - Criar endpoint específico para busca de hashtags
    - Ordenar posts por recência e popularidade
    - _Requirements: 9.5_
  
  - [x] 9.7 Escrever testes de propriedade para hashtags
    - **Property 47: Busca por hashtag ordena corretamente**
    - **Validates: Requirements 9.5**
  
  - [x] 9.8 Implementar autocomplete
    - Criar endpoint de autocomplete
    - Retornar sugestões baseadas em prefixo
    - _Requirements: 9.7_
  
  - [x] 9.9 Escrever testes de propriedade para autocomplete
    - **Property 49: Autocomplete retorna sugestões**
    - **Validates: Requirements 9.7**

- [x] 10. Checkpoint - Verificar Recommendation e Search
  - Garantir que todos os testes passam
  - Verificar consumo de eventos do Kafka
  - Testar geração de feed personalizado
  - Testar busca e autocomplete
  - Perguntar ao usuário se há dúvidas

- [x] 11. Implementar Messaging Service (Node.js + Socket.io)
  - [x] 11.1 Criar servidor Node.js com Socket.io
    - Configurar WebSocket server
    - Configurar MongoDB para histórico de mensagens
    - Configurar Redis para sessões WebSocket
    - _Requirements: 7.1_
  
  - [x] 11.2 Implementar autenticação WebSocket
    - Validar token JWT na conexão WebSocket
    - Armazenar sessão no Redis
    - _Requirements: 7.1_
  
  - [x] 11.3 Implementar envio de mensagens em tempo real
    - Criar handler para enviar mensagem de texto (1-5000 caracteres)
    - Entregar mensagem via WebSocket se destinatário online
    - Armazenar mensagem no MongoDB se destinatário offline
    - Enviar confirmação de entrega ao remetente
    - _Requirements: 7.1, 7.2, 7.6_
  
  - [x] 11.4 Escrever testes de propriedade para mensagens
    - **Property 32: Mensagem válida é entregue**
    - **Property 33: Mensagem para usuário offline é armazenada**
    - **Property 36: Entrega envia confirmação**
    - **Validates: Requirements 7.1, 7.2, 7.6**
  
  - [x] 11.5 Implementar envio de imagens em mensagens
    - Criar endpoint para upload de imagem
    - Fazer upload para S3
    - Enviar URL via WebSocket
    - _Requirements: 7.4_
  
  - [x] 11.6 Escrever testes de propriedade para imagens
    - **Property 34: Imagem em mensagem gera URL** (via mediaUrl no send_message)
    - **Validates: Requirements 7.4**
  
  - [x] 11.7 Implementar histórico de conversas
    - Criar endpoint para buscar histórico de conversa
    - Implementar paginação (50 mensagens por página)
    - Ordenar por timestamp
    - _Requirements: 7.5_
  
  - [x] 11.8 Escrever testes de propriedade para histórico
    - **Property 35: Histórico paginado com 50 mensagens**
    - **Validates: Requirements 7.5**
  
  - [x] 11.9 Implementar confirmações de leitura
    - Criar handler para marcar mensagem como lida
    - Enviar confirmação de leitura ao remetente via WebSocket
    - _Requirements: 7.7_
  
  - [x] 11.10 Escrever testes de propriedade para leitura
    - **Property 37: Leitura envia confirmação**
    - **Validates: Requirements 7.7**
  
  - [x] 11.11 Implementar lista de conversas
    - Criar endpoint para listar conversas do usuário
    - Incluir última mensagem e contador de não lidas
    - Ordenar por timestamp da última mensagem

- [x] 12. Implementar Notification Service (Python + FastAPI)
  - [x] 12.1 Criar projeto FastAPI com dependências
    - Configurar MongoDB para histórico de notificações
    - Configurar Firebase Cloud Messaging (FCM) e APNs
    - _Requirements: 8.1_
  
  - [x] 12.2 Implementar consumo de eventos
    - Consumir eventos do Kafka (like.created, comment.created, follow.created)
    - Criar notificações baseadas em eventos
    - _Requirements: 8.1, 8.2, 8.3_
  
  - [x] 12.3 Implementar agregação de notificações
    - Agregar múltiplos comentários no mesmo post em 5 minutos
    - Criar notificação única agregada
    - _Requirements: 8.2_
  
  - [x] 12.4 Escrever testes de propriedade para notificações
    - **Property 38: Curtida cria notificação**
    - **Property 39: Múltiplos comentários são agregados**
    - **Property 40: Novo seguidor cria notificação**
    - **Validates: Requirements 8.1, 8.2, 8.3**
  
  - [x] 12.5 Implementar preferências de notificação
    - Criar endpoint para configurar preferências
    - Respeitar preferências ao criar notificações
    - _Requirements: 8.4_
  
  - [x] 12.6 Escrever testes de propriedade para preferências
    - **Property 41: Preferências de notificação são respeitadas**
    - **Validates: Requirements 8.4**
  
  - [x] 12.7 Implementar envio de push notifications
    - Enviar via FCM para Android
    - Enviar via APNs para iOS
    - Apenas quando push está habilitado nas preferências
    - _Requirements: 8.5_
  
  - [x] 12.8 Escrever testes de propriedade para push
    - **Property 42: Push notification é enviado quando habilitado**
    - **Validates: Requirements 8.5**
  
  - [x] 12.9 Implementar gerenciamento de notificações
    - Criar endpoint para listar notificações do usuário
    - Criar endpoint para marcar como lida
    - Criar endpoint para deletar notificação
    - Implementar limpeza automática após 90 dias
    - _Requirements: 8.6, 8.7_
  
  - [x] 12.10 Escrever testes de propriedade para gerenciamento
    - **Property 43: Visualizar notificação marca como lida**
    - **Validates: Requirements 8.7**

- [x] 13. Checkpoint - Verificar Messaging e Notification
  - Garantir que todos os testes passam
  - Testar comunicação WebSocket
  - Verificar envio de push notifications
  - Testar agregação de notificações
  - Perguntar ao usuário se há dúvidas

- [x] 14. Implementar Segurança e Validação
  - [x] 14.1 Implementar sanitização de entradas
    - Adicionar middleware de sanitização no API Gateway
    - Prevenir SQL injection, XSS, code injection
    - Validar todos os inputs de usuário
    - _Requirements: 10.4_
  
  - [x] 14.2 Escrever testes de propriedade para sanitização
    - **Property 52: Entradas são sanitizadas**
    - **Validates: Requirements 10.4**
  
  - [x] 14.3 Implementar auditoria de acessos
    - Registrar tentativas de acesso suspeitas
    - Criar log de auditoria centralizado
    - Implementar alertas para padrões de abuso
    - _Requirements: 10.6, 10.7_
  
  - [x] 14.4 Escrever testes de propriedade para auditoria
    - **Property 53: Acessos suspeitos são registrados**
    - **Validates: Requirements 10.6**

- [x] 15. Implementar Conformidade e Privacidade (LGPD)
  - [x] 15.1 Implementar exportação de dados
    - Criar endpoint para exportar todos os dados do usuário em JSON
    - Incluir posts, comentários, mensagens, perfil
    - _Requirements: 14.1_
  
  - [x] 15.2 Escrever testes de propriedade para exportação
    - **Property 54: Exportação de dados retorna JSON completo**
    - **Validates: Requirements 14.1**
  
  - [x] 15.3 Implementar exclusão de dados (LGPD)
    - Criar endpoint para solicitar exclusão de conta
    - Remover dados pessoais de todos os microsserviços
    - Anonimizar conteúdo público (posts, comentários)
    - _Requirements: 14.2_
  
  - [x] 15.4 Escrever testes de propriedade para exclusão
    - **Property 55: Exclusão remove dados pessoais**
    - **Validates: Requirements 14.2**
  
  - [x] 15.5 Implementar criptografia de dados pessoais
    - Configurar criptografia AES-256 em repouso
    - Criptografar campos sensíveis (email, telefone, endereço)
    - _Requirements: 14.4_
  
  - [x] 15.6 Escrever testes de propriedade para criptografia
    - **Property 56: Dados pessoais são criptografados**
    - **Validates: Requirements 14.4**
  
  - [x] 15.7 Implementar controle de acesso baseado em roles
    - Criar sistema de roles (user, moderator, admin)
    - Implementar middleware de autorização
    - Restringir acesso a dados sensíveis
    - _Requirements: 14.5_
  
  - [x] 15.8 Escrever testes de propriedade para autorização
    - **Property 57: Controle de acesso baseado em roles**
    - **Validates: Requirements 14.5**
  
  - [x] 15.9 Implementar log de auditoria de dados pessoais
    - Registrar todos os acessos a dados pessoais
    - Incluir timestamp, usuário, ação, recurso
    - _Requirements: 14.6_
  
  - [x] 15.10 Escrever testes de propriedade para auditoria
    - **Property 58: Acessos a dados pessoais são auditados**
    - **Validates: Requirements 14.6**
  
  - [x] 15.11 Implementar configuração de privacidade de perfil
    - Criar endpoint para configurar perfil como público/privado
    - Restringir visualização de conteúdo de perfis privados
    - Apenas seguidores aprovados podem ver conteúdo privado
    - _Requirements: 14.7_
  
  - [x] 15.12 Escrever testes de propriedade para privacidade
    - **Property 59: Privacidade de perfil é respeitada**
    - **Validates: Requirements 14.7**

- [x] 16. Implementar APIs Públicas e Webhooks
  - [x] 16.1 Criar documentação OpenAPI 3.0
    - Documentar todos os endpoints públicos
    - Incluir exemplos de requisição e resposta
    - Publicar documentação via Swagger UI
    - _Requirements: 15.1_
  
  - [x] 16.2 Implementar versionamento de API
    - Adicionar prefixo /v1/ em todas as rotas
    - Preparar estrutura para /v2/ futuro
    - _Requirements: 15.2_
  
  - [x] 16.3 Implementar OAuth 2.0 para aplicações terceiras
    - Criar fluxo de autorização OAuth 2.0
    - Implementar geração de access tokens e refresh tokens
    - Criar endpoints de autorização e token
    - _Requirements: 15.4_
  
  - [x] 16.4 Implementar rate limiting por tier
    - Criar tiers de API (free, premium)
    - Configurar limites diferentes por tier
    - Free: 100 req/hora, Premium: 1000 req/hora
    - _Requirements: 15.5_
  
  - [x] 16.5 Escrever testes de propriedade para tiers
    - **Property 60: Rate limiting diferenciado por tier**
    - **Validates: Requirements 15.5**
  
  - [x] 16.6 Implementar sistema de webhooks
    - Criar endpoint para registrar webhooks
    - Publicar eventos importantes (novo post, nova mensagem)
    - Implementar retry com backoff exponencial
    - _Requirements: 15.6_
  
  - [x] 16.7 Escrever testes de propriedade para webhooks
    - **Property 61: Webhooks são chamados para eventos**
    - **Validates: Requirements 15.6**
  
  - [x] 16.8 Padronizar formato de erros
    - Criar estrutura ErrorResponse padronizada
    - Incluir código de erro, mensagem, requestId, timestamp
    - Aplicar em todos os microsserviços
    - _Requirements: 15.7_
  
  - [x] 16.9 Escrever testes de propriedade para erros
    - **Property 62: Erros retornam formato padronizado**
    - **Validates: Requirements 15.7**

- [x] 17. Checkpoint Final - Integração Completa
  - Garantir que todos os testes passam (unitários e de propriedade)
  - Testar fluxos end-to-end principais:
    - Registro → Login → Criar Post → Feed → Curtir → Notificação
    - Seguir Usuário → Ver Stories → Enviar Mensagem
    - Buscar Conteúdo → Compartilhar Post
  - Verificar comunicação entre todos os microsserviços
  - Validar publicação e consumo de eventos no Kafka
  - Testar rate limiting e segurança
  - Verificar conformidade com LGPD
  - Perguntar ao usuário se há dúvidas ou ajustes necessários

- [x] 18. Deploy e Monitoramento
  - [x] 18.1 Configurar Kubernetes para orquestração
    - Criar Deployments para cada microsserviço
    - Configurar Services e Ingress
    - Configurar HorizontalPodAutoscaler
    - _Requirements: 11.4_
  
  - [x] 18.2 Configurar monitoramento e observabilidade
    - Implementar coleta de métricas (Prometheus)
    - Configurar dashboards (Grafana)
    - Implementar alertas para erros e performance
    - _Requirements: 12.1, 12.4, 12.7_
  
  - [x] 18.3 Configurar backup e disaster recovery
    - Configurar backups automáticos de bancos de dados
    - Implementar replicação de dados críticos
    - Testar procedimento de restore
    - _Requirements: 13.1, 13.2, 13.5_

## Notes

- Todas as tarefas são obrigatórias para garantir cobertura completa desde o início
- Cada task referencia requisitos específicos para rastreabilidade
- Checkpoints garantem validação incremental
- Testes de propriedade validam corretude universal com mínimo 100 iterações
- Testes unitários validam exemplos específicos e casos de borda
- A implementação segue arquitetura de microsserviços com comunicação via Kafka
- Cada microsserviço pode ser desenvolvido e testado independentemente
- Property-based testing usa fast-check (Node.js), Hypothesis (Python) e jqwik (Java)
