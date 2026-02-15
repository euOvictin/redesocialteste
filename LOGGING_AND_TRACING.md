# Logging Estruturado e Distributed Tracing

Este documento descreve a estratégia de logging estruturado e distributed tracing implementada na Rede Social Brasileira.

## 📋 Visão Geral

Todos os microsserviços implementam:
- **Logging Estruturado**: Logs em formato JSON com contexto rico
- **Distributed Tracing**: Rastreamento de requisições através dos microsserviços
- **Correlation IDs**: Trace IDs únicos para correlacionar logs
- **Métricas**: Exposição de métricas no formato Prometheus

## 🔍 Logging Estruturado

### Formato de Log

Todos os logs seguem o formato estruturado JSON:

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "info",
  "service": "api-gateway",
  "message": "Request completed",
  "method": "GET",
  "path": "/api/v1/feed",
  "statusCode": 200,
  "duration": "145ms",
  "traceId": "1705315845123-a7b3c9d2e",
  "userId": "user-123"
}
```

### Níveis de Log

- **ERROR**: Erros que requerem atenção imediata
- **WARN**: Situações anormais que não impedem operação
- **INFO**: Eventos importantes do sistema
- **DEBUG**: Informações detalhadas para debugging

### Configuração

Variáveis de ambiente:
```bash
LOG_LEVEL=info        # debug, info, warn, error
LOG_FORMAT=json       # json ou console
```

## 🔗 Distributed Tracing

### Trace ID

Cada requisição recebe um Trace ID único que é propagado através de todos os microsserviços:

```
Formato: {timestamp}-{random}
Exemplo: 1705315845123-a7b3c9d2e
```

### Propagação

O Trace ID é propagado via header HTTP:
```
X-Trace-Id: 1705315845123-a7b3c9d2e
```

### Fluxo de Rastreamento

```
Cliente
  │
  ├─> API Gateway (gera Trace ID)
  │     │
  │     ├─> User Service (propaga Trace ID)
  │     │     └─> PostgreSQL
  │     │
  │     ├─> Content Service (propaga Trace ID)
  │     │     ├─> MongoDB
  │     │     └─> Kafka (publica evento)
  │     │
  │     └─> Recommendation Engine (propaga Trace ID)
  │           └─> Redis
  │
  └─> Resposta (inclui Trace ID no header)
```

## 🛠️ Implementação por Linguagem

### Node.js (Winston)

```javascript
const { createLogger, requestLogger, traceIdMiddleware } = require('../shared/config/logger');

// Criar logger
const logger = createLogger('api-gateway');

// Adicionar middlewares
app.use(traceIdMiddleware);
app.use(requestLogger(logger));

// Usar logger
logger.info('User authenticated', { userId: user.id });
logger.error('Database connection failed', { error: err.message });
```

### Python (structlog)

```python
from shared.config.logger import configure_logging, RequestLoggingMiddleware

# Configurar logger
logger = configure_logging('notification-service')

# Adicionar middleware
app.add_middleware(RequestLoggingMiddleware(logger))

# Usar logger
logger.info('notification_sent', user_id=user_id, type='push')
logger.error('fcm_error', error=str(e), user_id=user_id)
```

### Java (Spring Boot + Logback)

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

private static final Logger logger = LoggerFactory.getLogger(UserService.class);

// Adicionar Trace ID ao MDC
MDC.put("traceId", traceId);

// Usar logger
logger.info("User created: userId={}", user.getId());
logger.error("Database error", exception);

// Limpar MDC
MDC.clear();
```

## 📊 Integração com Jaeger

### Visualização de Traces

Acesse o Jaeger UI: `http://localhost:16686`

### Buscar Traces

1. Selecione o serviço (ex: api-gateway)
2. Defina o período de tempo
3. Adicione tags opcionais:
   - `http.method=GET`
   - `http.status_code=200`
   - `user.id=user-123`

### Análise de Performance

O Jaeger mostra:
- Duração total da requisição
- Tempo gasto em cada microsserviço
- Chamadas a bancos de dados
- Publicação/consumo de eventos Kafka
- Erros e exceções

## 📈 Métricas

### Endpoints de Métricas

Cada microsserviço expõe métricas:
- **Node.js**: `GET /metrics`
- **Java**: `GET /actuator/prometheus`
- **Python**: `GET /metrics`

### Métricas Principais

#### Requisições HTTP
```
http_requests_total{method="GET", path="/api/v1/feed", status="200"}
http_request_duration_seconds{method="GET", path="/api/v1/feed"}
```

#### Erros
```
http_errors_total{method="POST", path="/api/v1/posts", status="500"}
error_rate{service="content-service"}
```

#### Performance
```
request_duration_p50{service="api-gateway"}
request_duration_p95{service="api-gateway"}
request_duration_p99{service="api-gateway"}
```

#### Recursos
```
process_cpu_usage{service="user-service"}
process_memory_usage{service="user-service"}
db_connections_active{service="user-service"}
```

## 🔔 Alertas

### Configuração de Alertas

Alertas configurados no Prometheus:

#### Taxa de Erro Alta
```yaml
- alert: HighErrorRate
  expr: rate(http_errors_total[5m]) > 0.05
  for: 5m
  annotations:
    summary: "Taxa de erro alta no {{ $labels.service }}"
```

#### Latência Alta
```yaml
- alert: HighLatency
  expr: http_request_duration_seconds{quantile="0.95"} > 0.5
  for: 5m
  annotations:
    summary: "Latência P95 alta no {{ $labels.service }}"
```

#### Serviço Indisponível
```yaml
- alert: ServiceDown
  expr: up{job=~".*-service"} == 0
  for: 1m
  annotations:
    summary: "Serviço {{ $labels.job }} está indisponível"
```

## 🐛 Debugging

### Encontrar Logs por Trace ID

**Elasticsearch/Kibana:**
```
traceId: "1705315845123-a7b3c9d2e"
```

**Grep em arquivos:**
```bash
grep "1705315845123-a7b3c9d2e" logs/*.log
```

### Analisar Requisição Lenta

1. Identifique o Trace ID da requisição lenta
2. Busque no Jaeger UI
3. Analise o span de cada microsserviço
4. Identifique o gargalo (DB query, API externa, etc)
5. Busque logs detalhados usando o Trace ID

### Investigar Erro

1. Encontre o erro nos logs (nível ERROR)
2. Extraia o Trace ID
3. Visualize o trace completo no Jaeger
4. Identifique onde o erro ocorreu
5. Analise o contexto e stack trace

## 📝 Boas Práticas

### DO ✅

- Sempre propagar o Trace ID entre serviços
- Incluir contexto relevante nos logs (userId, postId, etc)
- Usar níveis de log apropriados
- Logar início e fim de operações importantes
- Incluir duração de operações
- Logar erros com stack trace completo

### DON'T ❌

- Não logar informações sensíveis (senhas, tokens)
- Não logar em excesso (evitar spam)
- Não usar `console.log` diretamente
- Não ignorar erros silenciosamente
- Não logar objetos muito grandes
- Não usar logs síncronos em produção

## 🔒 Segurança

### Dados Sensíveis

Nunca logar:
- Senhas
- Tokens de autenticação
- Chaves de API
- Dados de cartão de crédito
- Informações pessoais identificáveis (PII)

### Sanitização

Sempre sanitizar dados antes de logar:

```javascript
// ❌ Errado
logger.info('User login', { email, password });

// ✅ Correto
logger.info('User login', { email });
```

### Retenção de Logs

- **Logs de aplicação**: 30 dias
- **Logs de auditoria**: 90 dias
- **Logs de segurança**: 1 ano

## 📚 Recursos

- [Winston Documentation](https://github.com/winstonjs/winston)
- [structlog Documentation](https://www.structlog.org/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [OpenTelemetry](https://opentelemetry.io/)
