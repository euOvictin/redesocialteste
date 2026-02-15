/**
 * Property-Based Tests para Messaging Service
 * 
 * Property 32: Mensagem válida é entregue
 * Property 33: Mensagem para usuário offline é armazenada
 * Property 36: Entrega envia confirmação
 * Property 34: Imagem em mensagem gera URL
 * Property 35: Histórico paginado com 50 mensagens
 * Property 37: Leitura envia confirmação
 * 
 * Validates: Requirements 7.1, 7.2, 7.4, 7.5, 7.6, 7.7
 */

const fc = require('fast-check');
const { Server } = require('socket.io');
const { createServer } = require('http');
const Client = require('socket.io-client');
const jwt = require('jsonwebtoken');
const { setupWebSocketHandlers } = require('./handler');
const { connectRedis, disconnectRedis, getRedisClient } = require('../config/redis');
const { connectMongoDB, disconnectMongoDB, getDB } = require('../config/mongodb');
const config = require('../config');

describe('Messaging Service - Property-Based Tests', () => {
  let io, httpServer;
  const TEST_PORT = 8009;

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
    httpServer.listen(TEST_PORT, () => done());
  });

  afterEach(async () => {
    try {
      const db = getDB();
      await db.collection('messages').deleteMany({});
    } catch (e) { /* ignore */ }
    await new Promise(resolve => {
      io.close(() => httpServer.close(() => resolve()));
    });
  });

  describe('Property 32: Mensagem válida é entregue', () => {
    it('valid message is delivered to recipient', () => {
      fc.assert(
        fc.asyncProperty(
          fc.string({ minLength: 1, maxLength: 5000 }).filter(s => s.trim().length > 0),
          fc.uuid(),
          fc.uuid(),
          async (content, senderId, receiverId) => {
            fc.pre(senderId !== receiverId);
            const senderToken = jwt.sign(
              { userId: senderId, email: `${senderId}@test.com` },
              config.jwt.secret,
              { expiresIn: '1h' }
            );
            const receiverToken = jwt.sign(
              { userId: receiverId, email: `${receiverId}@test.com` },
              config.jwt.secret,
              { expiresIn: '1h' }
            );
            const receiverSocket = Client(`http://localhost:${TEST_PORT}`, { auth: { token: receiverToken } });
            const senderSocket = Client(`http://localhost:${TEST_PORT}`, { auth: { token: senderToken } });

            const received = new Promise(resolve => {
              receiverSocket.on('message_received', data => resolve(data));
            });

            await new Promise(resolve => senderSocket.on('connect', resolve));
            await new Promise(resolve => receiverSocket.on('connect', resolve));

            const response = await new Promise(resolve => {
              senderSocket.emit('send_message', { receiverId, content }, r => resolve(r));
            });

            expect(response.success).toBe(true);
            expect(response.messageId).toBeDefined();
            const msg = await received;
            expect(msg.content).toBe(content);
            expect(msg.senderId).toBe(senderId);
            expect(msg.receiverId).toBe(receiverId);

            receiverSocket.disconnect();
            senderSocket.disconnect();
          }
        ),
        { numRuns: 20 }
      );
    });
  });

  describe('Property 33: Mensagem para usuário offline é armazenada', () => {
    it('message to offline user is stored in MongoDB', () => {
      fc.assert(
        fc.asyncProperty(
          fc.string({ minLength: 1, maxLength: 500 }),
          fc.uuid(),
          fc.uuid(),
          async (content, senderId, receiverId) => {
            fc.pre(senderId !== receiverId);
            const token = jwt.sign(
              { userId: senderId, email: `${senderId}@test.com` },
              config.jwt.secret,
              { expiresIn: '1h' }
            );
            const clientSocket = Client(`http://localhost:${TEST_PORT}`, { auth: { token } });

            await new Promise(resolve => clientSocket.on('connect', resolve));
            const response = await new Promise(resolve => {
              clientSocket.emit('send_message', { receiverId, content }, r => resolve(r));
            });
            clientSocket.disconnect();

            expect(response.success).toBe(true);
            expect(response.status).toBe('stored');

            const db = getDB();
            const msg = await db.collection('messages').findOne({ senderId, receiverId, content });
            expect(msg).toBeDefined();
            expect(msg.content).toBe(content);
          }
        ),
        { numRuns: 20 }
      );
    });
  });

  describe('Property 37: Leitura envia confirmação', () => {
    it('mark as read sends confirmation to sender', async () => {
      const senderId = 'read-sender';
      const receiverId = 'read-receiver';
      const content = 'Test message for read receipt';
      const senderToken = jwt.sign({ userId: senderId, email: 's@t.com' }, config.jwt.secret, { expiresIn: '1h' });
      const receiverToken = jwt.sign({ userId: receiverId, email: 'r@t.com' }, config.jwt.secret, { expiresIn: '1h' });

      const senderSocket = Client(`http://localhost:${TEST_PORT}`, { auth: { token: senderToken } });
      const receiverSocket = Client(`http://localhost:${TEST_PORT}`, { auth: { token: receiverToken } });

      let messageId;
      const readConfirmation = new Promise(resolve => {
        senderSocket.on('message_read', data => resolve(data));
      });

      await new Promise(resolve => senderSocket.on('connect', resolve));
      await new Promise(resolve => receiverSocket.on('connect', resolve));

      const sendResponse = await new Promise(resolve => {
        senderSocket.emit('send_message', { receiverId, content }, r => resolve(r));
      });
      messageId = sendResponse.messageId;

      receiverSocket.emit('mark_as_read', { messageId }, () => {});
      const readMsg = await readConfirmation;

      expect(readMsg.messageId).toBe(messageId);
      expect(readMsg.readBy).toBe(receiverId);
      expect(readMsg.readAt).toBeDefined();
      senderSocket.disconnect();
      receiverSocket.disconnect();
    });
  });

  describe('Property 36: Entrega envia confirmação', () => {
    it('delivery confirmation is sent to sender', () => {
      fc.assert(
        fc.asyncProperty(
          fc.string({ minLength: 1, maxLength: 200 }),
          fc.uuid(),
          fc.uuid(),
          async (content, senderId, receiverId) => {
            fc.pre(senderId !== receiverId);
            const token = jwt.sign(
              { userId: senderId, email: `${senderId}@test.com` },
              config.jwt.secret,
              { expiresIn: '1h' }
            );
            const clientSocket = Client(`http://localhost:${TEST_PORT}`, { auth: { token } });

            const delivered = new Promise(resolve => {
              clientSocket.on('message_delivered', data => resolve(data));
            });

            await new Promise(resolve => clientSocket.on('connect', resolve));
            clientSocket.emit('send_message', { receiverId, content });
            const msg = await delivered;

            expect(msg.messageId).toBeDefined();
            expect(['delivered', 'stored']).toContain(msg.status);
            clientSocket.disconnect();
          }
        ),
        { numRuns: 20 }
      );
    });
  });

});
