# Messaging Service

Serviço de mensagens diretas em tempo real para a Rede Social Brasileira.

## Tecnologias

- **Node.js 18+**: Runtime JavaScript
- **Express**: Framework web
- **Socket.io**: WebSocket para comunicação em tempo real
- **MongoDB**: Armazenamento de histórico de mensagens
- **Redis**: Gerenciamento de sessões WebSocket
- **JWT**: Autenticação de usuários

## Funcionalidades

- ✅ Servidor WebSocket com Socket.io
- ✅ Autenticação JWT para conexões WebSocket
- ✅ Gerenciamento de sessões com Redis
- ✅ Armazenamento de mensagens no MongoDB
- ⏳ Envio de mensagens em tempo real
- ⏳ Confirmações de entrega e leitura
- ⏳ Histórico de conversas com paginação
- ⏳ Upload de imagens em mensagens

## Requisitos

- Node.js 18+
- MongoDB 7+
- Redis 7+

## Instalação

```bash
# Instalar dependências
npm install

# Copiar arquivo de configuração
cp .env.example .env

# Editar variáveis de ambiente
nano .env
```

## Configuração

Edite o arquivo `.env` com suas configurações:

```env
MESSAGING_SERVICE_PORT=8006
NODE_ENV=development
JWT_SECRET=your-secret-key-change-in-production
MONGODB_URI=mongodb://admin:admin@localhost:27017/rede_social?authSource=admin
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis123
```

## Executar

```bash
# Desenvolvimento (com hot reload)
npm run dev

# Produção
npm start

# Testes
npm test

# Testes com watch
npm run test:watch
```

## Docker

```bash
# Build da imagem
docker build -t messaging-service .

# Executar container
docker run -p 8006:8006 \
  -e MONGODB_URI=mongodb://admin:admin@mongodb:27017/rede_social?authSource=admin \
  -e REDIS_HOST=redis \
  messaging-service
```

## API Endpoints

### HTTP Endpoints

- `GET /health` - Health check
- `GET /ready` - Readiness check
- `GET /api/v1` - Informações da API

### WebSocket Events

#### Cliente → Servidor

- `send_message` - Enviar mensagem
  ```javascript
  socket.emit('send_message', {
    receiverId: 'user-id',
    content: 'Olá!',
    mediaUrl: 'https://...' // opcional
  }, (response) => {
    console.log(response); // { success: true, messageId: '...' }
  });
  ```

- `mark_as_read` - Marcar mensagem como lida
  ```javascript
  socket.emit('mark_as_read', {
    messageId: 'message-id'
  }, (response) => {
    console.log(response); // { success: true }
  });
  ```

#### Servidor → Cliente

- `message_received` - Nova mensagem recebida
- `message_delivered` - Confirmação de entrega
- `message_read` - Confirmação de leitura

## Conexão WebSocket

### JavaScript/TypeScript

```javascript
import io from 'socket.io-client';

const socket = io('http://localhost:8006', {
  auth: {
    token: 'your-jwt-token'
  }
});

socket.on('connect', () => {
  console.log('Connected to messaging service');
});

socket.on('message_received', (message) => {
  console.log('New message:', message);
});
```

### React Native

```javascript
import io from 'socket.io-client';

const socket = io('http://localhost:8006', {
  auth: {
    token: userToken
  },
  transports: ['websocket']
});
```

## Estrutura do Projeto

```
messaging-service/
├── src/
│   ├── config/          # Configurações
│   │   ├── index.js     # Config principal
│   │   ├── mongodb.js   # Conexão MongoDB
│   │   └── redis.js     # Conexão Redis
│   ├── middleware/      # Middlewares
│   │   ├── auth.js      # Autenticação JWT
│   │   └── errorHandler.js
│   ├── websocket/       # Handlers WebSocket
│   │   └── handler.js   # Handlers Socket.io
│   ├── utils/           # Utilitários
│   │   └── logger.js    # Logger Winston
│   ├── app.js           # Configuração Express + Socket.io
│   └── index.js         # Entry point
├── .env.example         # Exemplo de variáveis de ambiente
├── .dockerignore
├── Dockerfile
├── package.json
└── README.md
```

## Monitoramento

O serviço expõe métricas e logs estruturados:

- Logs em formato JSON via Winston
- Health checks em `/health` e `/ready`
- Request IDs para rastreamento distribuído

## Segurança

- Autenticação JWT obrigatória para WebSocket
- Validação de entrada em todos os eventos
- Rate limiting (a ser implementado)
- Sanitização de dados
- CORS configurável

## Próximos Passos

1. Implementar envio de mensagens (Task 11.3)
2. Implementar histórico de conversas (Task 11.7)
3. Implementar confirmações de leitura (Task 11.9)
4. Adicionar testes unitários e de propriedade
5. Implementar rate limiting para WebSocket

## Licença

MIT
