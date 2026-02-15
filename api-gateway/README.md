# API Gateway - Rede Social Brasileira

API Gateway é o ponto de entrada único para todos os microsserviços da Rede Social Brasileira.

## Funcionalidades

- ✅ Roteamento de requisições para microsserviços
- ✅ Autenticação JWT
- ✅ Rate limiting
- ✅ Circuit breaker e retry logic
- ✅ CORS e segurança (Helmet)
- ✅ Logging estruturado
- ✅ Health checks
- ✅ Tratamento de erros padronizado

## Instalação

```bash
npm install
```

## Configuração

Copie o arquivo `.env.example` para `.env` e configure as variáveis:

```bash
cp .env.example .env
```

## Executar

### Desenvolvimento
```bash
npm run dev
```

### Produção
```bash
npm start
```

## Testes

```bash
# Executar todos os testes
npm test

# Executar testes em modo watch
npm run test:watch
```

## Endpoints

### Health Checks
- `GET /health` - Status de saúde do serviço
- `GET /ready` - Status de prontidão do serviço

### API v1
- `GET /api/v1` - Informações da API e endpoints disponíveis

### Autenticação
- `POST /api/v1/auth/register` - Registrar novo usuário
- `POST /api/v1/auth/login` - Login de usuário
- `POST /api/v1/auth/refresh` - Renovar token
- `POST /api/v1/auth/logout` - Logout de usuário

## Docker

```bash
# Build
docker build -t api-gateway .

# Run
docker run -p 3000:3000 --env-file .env api-gateway
```

## Arquitetura

O API Gateway atua como proxy reverso, roteando requisições para os microsserviços apropriados:

```
Cliente → API Gateway → [User Service | Content Service | Messaging Service | ...]
```

## Rate Limiting

- **Geral**: 100 requisições por minuto por usuário
- **Autenticação**: 10 requisições por minuto

## Circuit Breaker

- **Timeout**: 30 segundos
- **Error Threshold**: 50%
- **Reset Timeout**: 30 segundos
