BACKEND
# Rede Social Brasileira

Uma plataforma moderna de mídia social que combina características do Instagram e Twitter, construída com arquitetura de microsserviços para garantir escalabilidade, manutenibilidade e alta disponibilidade.

## 🏗️ Arquitetura

A plataforma é composta por 7 microsserviços:

1. **API Gateway** (Node.js) - Ponto de entrada único, autenticação, rate limiting
2. **User Service** (Java + Spring Boot) - Gerenciamento de usuários e relacionamentos
3. **Content Service** (Java + Spring Boot) - Posts, stories e interações sociais
4. **Messaging Service** (Node.js + Socket.io) - Mensagens diretas em tempo real
5. **Notification Service** (Python + FastAPI) - Sistema de notificações push
6. **Search Service** (Python + FastAPI) - Busca e indexação de conteúdo
7. **Recommendation Engine** (Python + FastAPI) - Feed personalizado e recomendações

## 🛠️ Tecnologias

### Backend
- **Node.js** - API Gateway, Messaging Service
- **Java + Spring Boot** - User Service, Content Service
- **Python + FastAPI** - Notification, Search, Recommendation

### Bancos de Dados
- **PostgreSQL** - Dados relacionais (usuários, metadados)
- **MongoDB** - Dados não estruturados (posts, mensagens, notificações)
- **Redis** - Cache distribuído e sessões
- **Elasticsearch** - Motor de busca full-text

### Infraestrutura
- **Apache Kafka** - Message broker para comunicação assíncrona
- **Docker & Docker Compose** - Containerização
- **Jaeger** - Distributed tracing
- **Prometheus** - Coleta de métricas
- **Grafana** - Visualização de métricas

## 🚀 Início Rápido

### Pré-requisitos

- Docker 20.10+
- Docker Compose 2.0+
- Node.js 18+ (para desenvolvimento local)
- Java 17+ (para desenvolvimento local)
- Python 3.11+ (para desenvolvimento local)

### Configuração

1. Clone o repositório:
```bash
git clone https://github.com/seu-usuario/rede-social-brasileira.git
cd rede-social-brasileira
```

2. Copie o arquivo de variáveis de ambiente:
```bash
cp .env.example .env
```

3. Edite o arquivo `.env` com suas configurações (opcional para desenvolvimento)

### Iniciar Infraestrutura

Inicie todos os serviços de infraestrutura (PostgreSQL, MongoDB, Redis, Kafka, Elasticsearch, etc):

```bash
docker-compose up -d
```

Verificar status dos containers:
```bash
docker-compose ps
```

Verificar logs:
```bash
docker-compose logs -f [nome-do-serviço]
```

### Acessar Serviços

Após iniciar o Docker Compose, os seguintes serviços estarão disponíveis:

- **PostgreSQL**: `localhost:5432`
  - Usuário: `postgres`
  - Senha: `postgres`
  - Database: `rede_social`

- **MongoDB**: `localhost:27017`
  - Usuário: `admin`
  - Senha: `admin`
  - Database: `rede_social`

- **Redis**: `localhost:6379`
  - Senha: `redis123`

- **Kafka**: `localhost:9093` (para aplicações externas)

- **Elasticsearch**: `http://localhost:9200`

- **Jaeger UI**: `http://localhost:16686`

- **Prometheus**: `http://localhost:9090`

- **Grafana**: `http://localhost:3000`
  - Usuário: `admin`
  - Senha: `admin`

## 📁 Estrutura do Projeto

```
rede-social-brasileira/
├── api-gateway/              # API Gateway (Node.js)
├── user-service/             # User Service (Java + Spring Boot)
├── content-service/          # Content Service (Java + Spring Boot)
├── messaging-service/        # Messaging Service (Node.js + Socket.io)
├── notification-service/     # Notification Service (Python + FastAPI)
├── search-service/           # Search Service (Python + FastAPI)
├── recommendation-engine/    # Recommendation Engine (Python + FastAPI)
├── shared/                   # Código compartilhado
│   └── config/              # Configurações compartilhadas
│       ├── logger.js        # Logger para Node.js
│       └── logger.py        # Logger para Python
├── scripts/                  # Scripts de inicialização
│   ├── init-postgres.sql    # Inicialização do PostgreSQL
│   └── init-mongo.js        # Inicialização do MongoDB
├── config/                   # Configurações de infraestrutura
│   ├── prometheus.yml       # Configuração do Prometheus
│   └── grafana/             # Configurações do Grafana
├── .kiro/                    # Especificações do projeto
│   └── specs/
│       └── rede-social-brasileira/
│           ├── requirements.md
│           ├── design.md
│           └── tasks.md
├── docker-compose.yml        # Orquestração de containers
├── .env                      # Variáveis de ambiente
├── .env.example             # Exemplo de variáveis de ambiente
└── README.md                # Este arquivo
```

## 🔧 Desenvolvimento

### Logging Estruturado

Todos os microsserviços utilizam logging estruturado com suporte a distributed tracing:

**Node.js (Winston):**
```javascript
const { createLogger, requestLogger, traceIdMiddleware } = require('../shared/config/logger');

const logger = createLogger('api-gateway');
app.use(traceIdMiddleware);
app.use(requestLogger(logger));

logger.info('Server started', { port: 3000 });
```

**Python (structlog):**
```python
from shared.config.logger import configure_logging, RequestLoggingMiddleware

logger = configure_logging('notification-service')
app.add_middleware(RequestLoggingMiddleware(logger))

logger.info('service_started', version='1.0.0')
```

### Distributed Tracing

Todos os microsserviços propagam o header `X-Trace-Id` para rastreamento distribuído. Visualize traces no Jaeger UI: `http://localhost:16686`

### Métricas

Cada microsserviço expõe métricas no formato Prometheus:
- Node.js: `/metrics`
- Java (Spring Boot): `/actuator/prometheus`
- Python (FastAPI): `/metrics`

Visualize métricas no Grafana: `http://localhost:3000`

## 🧪 Testes

O projeto utiliza uma estratégia dual de testes:

1. **Testes Unitários** - Casos específicos e edge cases
2. **Property-Based Tests** - Validação de propriedades universais

### Executar Testes

```bash
# Node.js
cd api-gateway
npm test

# Java
cd user-service
./mvnw test

# Python
cd notification-service
pytest
```

## 📊 Monitoramento

### Health Checks

Todos os microsserviços expõem endpoints de health check:
- Node.js: `GET /health`
- Java: `GET /actuator/health`
- Python: `GET /health`

### Métricas Principais

- Latência (P50, P95, P99)
- Throughput (requisições/segundo)
- Taxa de erro
- Utilização de recursos

### Alertas

Configure alertas no Prometheus para:
- Taxa de erro > 5%
- Latência P95 > 500ms
- Serviço indisponível

## 🔒 Segurança

- Autenticação JWT com tokens de 24 horas
- Rate limiting (100 req/min por usuário)
- Sanitização de entradas
- Criptografia AES-256 para dados sensíveis
- CORS configurável
- Logs de auditoria

## 📝 Conformidade LGPD

- Exportação de dados em JSON
- Exclusão de dados pessoais
- Controle de privacidade de perfil
- Logs de auditoria de acessos

## 🤝 Contribuindo

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo `LICENSE` para mais detalhes.

## 📞 Contato

Equipe de Desenvolvimento - [@redesocialbr](https://twitter.com/redesocialbr)

Link do Projeto: [https://github.com/seu-usuario/rede-social-brasileira](https://github.com/seu-usuario/rede-social-brasileira)

## 🙏 Agradecimentos

- [Spring Boot](https://spring.io/projects/spring-boot)
- [FastAPI](https://fastapi.tiangolo.com/)
- [Express.js](https://expressjs.com/)
- [Socket.io](https://socket.io/)
- [Apache Kafka](https://kafka.apache.org/)
- [Elasticsearch](https://www.elastic.co/)

