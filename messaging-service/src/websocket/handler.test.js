const { Server } = require('socket.io');
const { createServer } = require('http');
const Client = require('socket.io-client');
const jwt = require('jsonwebtoken');
const { setupWebSocketHandlers } = require('./handler');
const { connectRedis, disconnectRedis, getRedisClient } = require('../config/redis');
const { connectMongoDB, disconnectMongoDB, getDB } = require('../config/mongodb');
const config = require('../config');

describe('WebSocket Authentication Tests', () => {
  let io, serverSocket, clientSocket, httpServer;
  const TEST_PORT = 8007;

  beforeAll(async () => {
    // Conectar ao Redis e MongoDB
    await connectRedis();
    await connectMongoDB();
  });

  afterAll(async () => {
    // Desconectar do Redis e MongoDB
    await disconnectRedis();
    await disconnectMongoDB();
  });

  beforeEach((done) => {
    httpServer = createServer();
    io = new Server(httpServer);
    setupWebSocketHandlers(io);
    
    httpServer.listen(TEST_PORT, () => {
      done();
    });
  });

  afterEach((done) => {
    if (clientSocket && clientSocket.connected) {
      clientSocket.disconnect();
    }
    
    io.close(() => {
      httpServer.close(() => {
        done();
      });
    });
  });

  describe('Authentication', () => {
    it('should reject connection without token', (done) => {
      clientSocket = Client(`http://localhost:${TEST_PORT}`);
      
      clientSocket.on('connect_error', (error) => {
        expect(error.message).toBe('Authentication failed');
        done();
      });
    });

    it('should reject connection with invalid token', (done) => {
      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: 'invalid-token' }
      });
      
      clientSocket.on('connect_error', (error) => {
        expect(error.message).toBe('Authentication failed');
        done();
      });
    });

    it('should reject connection with expired token', (done) => {
      const expiredToken = jwt.sign(
        { userId: 'user-123', email: 'test@example.com' },
        config.jwt.secret,
        { expiresIn: '-1h' } // Token expirado
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: expiredToken }
      });
      
      clientSocket.on('connect_error', (error) => {
        expect(error.message).toBe('Authentication failed');
        done();
      });
    });

    it('should accept connection with valid token via auth', (done) => {
      const validToken = jwt.sign(
        { userId: 'user-123', email: 'test@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: validToken }
      });
      
      clientSocket.on('connect', () => {
        expect(clientSocket.connected).toBe(true);
        done();
      });

      clientSocket.on('connect_error', (error) => {
        done(error);
      });
    });

    it('should accept connection with valid token via query', (done) => {
      const validToken = jwt.sign(
        { userId: 'user-456', email: 'test2@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        query: { token: validToken }
      });
      
      clientSocket.on('connect', () => {
        expect(clientSocket.connected).toBe(true);
        done();
      });

      clientSocket.on('connect_error', (error) => {
        done(error);
      });
    });
  });

  describe('Session Management', () => {
    it('should store session in Redis on connection', (done) => {
      const userId = 'user-789';
      const validToken = jwt.sign(
        { userId, email: 'test3@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: validToken }
      });
      
      clientSocket.on('connect', async () => {
        try {
          const redis = getRedisClient();
          const sessionId = await redis.get(`ws:session:${userId}`);
          
          expect(sessionId).toBeDefined();
          expect(sessionId).toBe(clientSocket.id);
          
          // Verificar TTL
          const ttl = await redis.ttl(`ws:session:${userId}`);
          expect(ttl).toBeGreaterThan(0);
          expect(ttl).toBeLessThanOrEqual(3600);
          
          done();
        } catch (error) {
          done(error);
        }
      });

      clientSocket.on('connect_error', (error) => {
        done(error);
      });
    });

    it('should remove session from Redis on disconnection', (done) => {
      const userId = 'user-disconnect';
      const validToken = jwt.sign(
        { userId, email: 'disconnect@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: validToken }
      });
      
      clientSocket.on('connect', async () => {
        try {
          const redis = getRedisClient();
          
          // Verificar que a sessão existe
          let sessionId = await redis.get(`ws:session:${userId}`);
          expect(sessionId).toBeDefined();
          
          // Desconectar
          clientSocket.disconnect();
          
          // Aguardar um pouco para o handler processar
          setTimeout(async () => {
            try {
              sessionId = await redis.get(`ws:session:${userId}`);
              expect(sessionId).toBeNull();
              done();
            } catch (error) {
              done(error);
            }
          }, 100);
        } catch (error) {
          done(error);
        }
      });

      clientSocket.on('connect_error', (error) => {
        done(error);
      });
    });

    it('should join user room on connection', (done) => {
      const userId = 'user-room';
      const validToken = jwt.sign(
        { userId, email: 'room@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      io.on('connection', (socket) => {
        // Verificar que o socket entrou na sala do usuário
        const rooms = Array.from(socket.rooms);
        expect(rooms).toContain(`user:${userId}`);
        done();
      });

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: validToken }
      });

      clientSocket.on('connect_error', (error) => {
        done(error);
      });
    });
  });

  describe('Multiple Connections', () => {
    it('should handle multiple authenticated connections', (done) => {
      const token1 = jwt.sign(
        { userId: 'user-multi-1', email: 'multi1@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const token2 = jwt.sign(
        { userId: 'user-multi-2', email: 'multi2@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const client1 = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: token1 }
      });

      const client2 = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: token2 }
      });

      let connectedCount = 0;

      const checkBothConnected = () => {
        connectedCount++;
        if (connectedCount === 2) {
          expect(client1.connected).toBe(true);
          expect(client2.connected).toBe(true);
          
          client1.disconnect();
          client2.disconnect();
          done();
        }
      };

      client1.on('connect', checkBothConnected);
      client2.on('connect', checkBothConnected);

      client1.on('connect_error', done);
      client2.on('connect_error', done);
    });

    it('should update session when same user reconnects', (done) => {
      const userId = 'user-reconnect';
      const validToken = jwt.sign(
        { userId, email: 'reconnect@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const client1 = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: validToken }
      });

      client1.on('connect', async () => {
        try {
          const redis = getRedisClient();
          const firstSocketId = await redis.get(`ws:session:${userId}`);
          expect(firstSocketId).toBe(client1.id);

          // Criar segunda conexão com o mesmo usuário
          const client2 = Client(`http://localhost:${TEST_PORT}`, {
            auth: { token: validToken }
          });

          client2.on('connect', async () => {
            try {
              const secondSocketId = await redis.get(`ws:session:${userId}`);
              expect(secondSocketId).toBe(client2.id);
              expect(secondSocketId).not.toBe(firstSocketId);

              client1.disconnect();
              client2.disconnect();
              done();
            } catch (error) {
              done(error);
            }
          });

          client2.on('connect_error', done);
        } catch (error) {
          done(error);
        }
      });

      client1.on('connect_error', done);
    });
  });

  describe('Error Handling', () => {
    it('should handle socket errors gracefully', (done) => {
      const validToken = jwt.sign(
        { userId: 'user-error', email: 'error@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: validToken }
      });

      clientSocket.on('connect', () => {
        // Emitir erro no socket
        clientSocket.emit('error', new Error('Test error'));
        
        // Se não houver crash, o teste passa
        setTimeout(() => {
          expect(clientSocket.connected).toBe(true);
          done();
        }, 100);
      });

      clientSocket.on('connect_error', done);
    });
  });
});

describe('Message Sending Tests', () => {
  let io, serverSocket, clientSocket, httpServer;
  const TEST_PORT = 8008;

  beforeAll(async () => {
    await connectRedis();
    await connectMongoDB();
  });

  afterAll(async () => {
    await disconnectRedis();
    await disconnectMongoDB();
  });

  beforeEach((done) => {
    httpServer = createServer();
    io = new Server(httpServer);
    setupWebSocketHandlers(io);
    
    httpServer.listen(TEST_PORT, () => {
      done();
    });
  });

  afterEach(async () => {
    if (clientSocket && clientSocket.connected) {
      clientSocket.disconnect();
    }
    
    // Limpar mensagens de teste do MongoDB
    try {
      const db = getDB();
      await db.collection('messages').deleteMany({ 
        $or: [
          { senderId: { $regex: /^test-/ } },
          { receiverId: { $regex: /^test-/ } }
        ]
      });
    } catch (error) {
      // Ignorar erros de limpeza
    }

    await new Promise((resolve) => {
      io.close(() => {
        httpServer.close(() => {
          resolve();
        });
      });
    });
  });

  describe('Valid Message Sending', () => {
    it('should send message with valid content (1-5000 characters)', (done) => {
      const senderId = 'test-sender-1';
      const receiverId = 'test-receiver-1';
      const content = 'Hello, this is a test message!';

      const token = jwt.sign(
        { userId: senderId, email: 'sender@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        clientSocket.emit('send_message', { receiverId, content }, (response) => {
          expect(response.success).toBe(true);
          expect(response.messageId).toBeDefined();
          expect(response.createdAt).toBeDefined();
          expect(response.status).toBe('stored'); // Receiver offline
          done();
        });
      });

      clientSocket.on('connect_error', done);
    });

    it('should store message in MongoDB', (done) => {
      const senderId = 'test-sender-2';
      const receiverId = 'test-receiver-2';
      const content = 'Test message for MongoDB storage';

      const token = jwt.sign(
        { userId: senderId, email: 'sender2@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        clientSocket.emit('send_message', { receiverId, content }, async (response) => {
          try {
            expect(response.success).toBe(true);
            
            // Verificar no MongoDB
            const db = getDB();
            const message = await db.collection('messages').findOne({
              senderId,
              receiverId,
              content
            });

            expect(message).toBeDefined();
            expect(message.senderId).toBe(senderId);
            expect(message.receiverId).toBe(receiverId);
            expect(message.content).toBe(content);
            expect(message.isRead).toBe(false);
            expect(message.createdAt).toBeInstanceOf(Date);
            
            done();
          } catch (error) {
            done(error);
          }
        });
      });

      clientSocket.on('connect_error', done);
    });

    it('should deliver message to online receiver via WebSocket', (done) => {
      const senderId = 'test-sender-3';
      const receiverId = 'test-receiver-3';
      const content = 'Message for online receiver';

      const senderToken = jwt.sign(
        { userId: senderId, email: 'sender3@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const receiverToken = jwt.sign(
        { userId: receiverId, email: 'receiver3@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      // Conectar receiver primeiro
      const receiverSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token: receiverToken }
      });

      receiverSocket.on('connect', () => {
        // Receiver está online, agora conectar sender
        clientSocket = Client(`http://localhost:${TEST_PORT}`, {
          auth: { token: senderToken }
        });

        // Receiver deve receber a mensagem
        receiverSocket.on('message_received', (data) => {
          expect(data.senderId).toBe(senderId);
          expect(data.receiverId).toBe(receiverId);
          expect(data.content).toBe(content);
          expect(data.messageId).toBeDefined();
          expect(data.deliveredAt).toBeDefined();
          
          receiverSocket.disconnect();
          done();
        });

        clientSocket.on('connect', () => {
          clientSocket.emit('send_message', { receiverId, content }, (response) => {
            expect(response.success).toBe(true);
            expect(response.status).toBe('delivered'); // Receiver online
            expect(response.deliveredAt).toBeDefined();
          });
        });
      });

      clientSocket.on('connect_error', done);
      receiverSocket.on('connect_error', done);
    });

    it('should send delivery confirmation to sender', (done) => {
      const senderId = 'test-sender-4';
      const receiverId = 'test-receiver-4';
      const content = 'Message with delivery confirmation';

      const token = jwt.sign(
        { userId: senderId, email: 'sender4@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        // Listen for delivery confirmation
        clientSocket.on('message_delivered', (data) => {
          expect(data.messageId).toBeDefined();
          expect(data.status).toBe('stored');
          done();
        });

        clientSocket.emit('send_message', { receiverId, content });
      });

      clientSocket.on('connect_error', done);
    });
  });

  describe('Message Validation', () => {
    it('should reject message without receiverId', (done) => {
      const senderId = 'test-sender-5';
      const content = 'Message without receiver';

      const token = jwt.sign(
        { userId: senderId, email: 'sender5@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        clientSocket.emit('send_message', { content }, (response) => {
          expect(response.success).toBe(false);
          expect(response.error.code).toBe('INVALID_DATA');
          done();
        });
      });

      clientSocket.on('connect_error', done);
    });

    it('should reject message without content', (done) => {
      const senderId = 'test-sender-6';
      const receiverId = 'test-receiver-6';

      const token = jwt.sign(
        { userId: senderId, email: 'sender6@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        clientSocket.emit('send_message', { receiverId }, (response) => {
          expect(response.success).toBe(false);
          expect(response.error.code).toBe('INVALID_DATA');
          done();
        });
      });

      clientSocket.on('connect_error', done);
    });

    it('should reject message with content less than 1 character', (done) => {
      const senderId = 'test-sender-7';
      const receiverId = 'test-receiver-7';
      const content = '';

      const token = jwt.sign(
        { userId: senderId, email: 'sender7@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        clientSocket.emit('send_message', { receiverId, content }, (response) => {
          expect(response.success).toBe(false);
          expect(response.error.code).toBe('MESSAGE_TOO_LONG');
          done();
        });
      });

      clientSocket.on('connect_error', done);
    });

    it('should reject message with content more than 5000 characters', (done) => {
      const senderId = 'test-sender-8';
      const receiverId = 'test-receiver-8';
      const content = 'a'.repeat(5001);

      const token = jwt.sign(
        { userId: senderId, email: 'sender8@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        clientSocket.emit('send_message', { receiverId, content }, (response) => {
          expect(response.success).toBe(false);
          expect(response.error.code).toBe('MESSAGE_TOO_LONG');
          done();
        });
      });

      clientSocket.on('connect_error', done);
    });

    it('should accept message with exactly 1 character', (done) => {
      const senderId = 'test-sender-9';
      const receiverId = 'test-receiver-9';
      const content = 'a';

      const token = jwt.sign(
        { userId: senderId, email: 'sender9@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        clientSocket.emit('send_message', { receiverId, content }, (response) => {
          expect(response.success).toBe(true);
          done();
        });
      });

      clientSocket.on('connect_error', done);
    });

    it('should accept message with exactly 5000 characters', (done) => {
      const senderId = 'test-sender-10';
      const receiverId = 'test-receiver-10';
      const content = 'a'.repeat(5000);

      const token = jwt.sign(
        { userId: senderId, email: 'sender10@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        clientSocket.emit('send_message', { receiverId, content }, (response) => {
          expect(response.success).toBe(true);
          done();
        });
      });

      clientSocket.on('connect_error', done);
    });

    it('should reject message to self', (done) => {
      const userId = 'test-sender-11';
      const content = 'Message to myself';

      const token = jwt.sign(
        { userId, email: 'sender11@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        clientSocket.emit('send_message', { receiverId: userId, content }, (response) => {
          expect(response.success).toBe(false);
          expect(response.error.code).toBe('INVALID_RECEIVER');
          done();
        });
      });

      clientSocket.on('connect_error', done);
    });
  });

  describe('Message with Media', () => {
    it('should send message with mediaUrl', (done) => {
      const senderId = 'test-sender-12';
      const receiverId = 'test-receiver-12';
      const content = 'Check out this image!';
      const mediaUrl = 'https://example.com/image.jpg';

      const token = jwt.sign(
        { userId: senderId, email: 'sender12@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      clientSocket = Client(`http://localhost:${TEST_PORT}`, {
        auth: { token }
      });

      clientSocket.on('connect', () => {
        clientSocket.emit('send_message', { receiverId, content, mediaUrl }, async (response) => {
          try {
            expect(response.success).toBe(true);
            
            // Verificar no MongoDB
            const db = getDB();
            const message = await db.collection('messages').findOne({
              senderId,
              receiverId
            });

            expect(message.mediaUrl).toBe(mediaUrl);
            done();
          } catch (error) {
            done(error);
          }
        });
      });

      clientSocket.on('connect_error', done);
    });
  });
});
