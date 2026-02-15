/**
 * Property-Based Tests para Message Routes
 * 
 * Property 35: Histórico paginado com 50 mensagens
 * Validates: Requirements 7.5
 */

const fc = require('fast-check');
const request = require('supertest');
const jwt = require('jsonwebtoken');
const { app } = require('../app');
const { connectRedis, disconnectRedis } = require('../config/redis');
const { connectMongoDB, disconnectMongoDB, getDB } = require('../config/mongodb');
const config = require('../config');

describe('Message Routes - Property-Based Tests', () => {
  beforeAll(async () => {
    await connectRedis();
    await connectMongoDB();
  });

  afterAll(async () => {
    await disconnectRedis();
    await disconnectMongoDB();
  });

  beforeEach(async () => {
    try {
      const db = getDB();
      await db.collection('messages').deleteMany({});
    } catch (e) { /* ignore */ }
  });

  describe('Property 35: Histórico paginado com 50 mensagens', () => {
    it('history returns up to 50 messages per page', async () => {
      const db = getDB();
      const senderId = 'hist-sender';
      const receiverId = 'hist-receiver';

      const msgs = Array.from({ length: 75 }, (_, i) => ({
        senderId,
        receiverId,
        content: `msg-${i}`,
        isRead: false,
        createdAt: new Date(Date.now() - (75 - i) * 1000)
      }));
      await db.collection('messages').insertMany(msgs);

      const token = jwt.sign(
        { userId: senderId, email: 'sender@test.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const response = await request(app)
        .get(`/api/v1/messages?otherUserId=${receiverId}&page=1&limit=50`)
        .set('Authorization', `Bearer ${token}`);

      expect(response.status).toBe(200);
      expect(response.body.messages.length).toBeLessThanOrEqual(50);
      expect(response.body.pagination.limit).toBe(50);
      expect(response.body.pagination.total).toBe(75);
    });

    it('pagination respects page and limit for any valid params', () => {
      fc.assert(
        fc.asyncProperty(
          fc.integer({ min: 1, max: 10 }),
          fc.integer({ min: 1, max: 50 }),
          async (page, limit) => {
            const db = getDB();
            const senderId = 'pbt-sender';
            const receiverId = 'pbt-receiver';
            const count = page * limit + 5;
            const msgs = Array.from({ length: count }, (_, i) => ({
              senderId,
              receiverId,
              content: `msg-${i}`,
              isRead: false,
              createdAt: new Date(Date.now() - (count - i) * 1000)
            }));
            await db.collection('messages').deleteMany({ senderId, receiverId });
            await db.collection('messages').insertMany(msgs);

            const token = jwt.sign(
              { userId: senderId, email: 'sender@test.com' },
              config.jwt.secret,
              { expiresIn: '1h' }
            );

            const response = await request(app)
              .get(`/api/v1/messages?otherUserId=${receiverId}&page=${page}&limit=${limit}`)
              .set('Authorization', `Bearer ${token}`);

            expect(response.status).toBe(200);
            expect(response.body.pagination.page).toBe(page);
            expect(response.body.pagination.limit).toBe(limit);
            expect(response.body.messages.length).toBeLessThanOrEqual(limit);
          }
        ),
        { numRuns: 10 }
      );
    });
  });
});
