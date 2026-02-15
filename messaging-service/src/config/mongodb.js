const { MongoClient } = require('mongodb');
const config = require('./index');
const logger = require('../utils/logger');

let client = null;
let db = null;

async function connectMongoDB() {
  try {
    if (client && db) {
      logger.info({ message: 'MongoDB already connected' });
      return { client, db };
    }

    logger.info({ message: 'Connecting to MongoDB...', uri: config.mongodb.uri.replace(/\/\/.*@/, '//***@') });
    
    client = new MongoClient(config.mongodb.uri, config.mongodb.options);
    await client.connect();
    
    db = client.db(config.mongodb.dbName);
    
    // Verificar conexão
    await db.command({ ping: 1 });
    
    logger.info({ message: 'MongoDB connected successfully', database: config.mongodb.dbName });
    
    // Criar índices
    await createIndexes(db);
    
    return { client, db };
  } catch (error) {
    logger.error({ message: 'MongoDB connection failed', error: error.message, stack: error.stack });
    throw error;
  }
}

async function createIndexes(database) {
  try {
    const messagesCollection = database.collection('messages');
    
    // Índice para buscar mensagens por conversa
    await messagesCollection.createIndex(
      { senderId: 1, receiverId: 1, createdAt: -1 },
      { name: 'conversation_index' }
    );
    
    // Índice para buscar mensagens não lidas
    await messagesCollection.createIndex(
      { receiverId: 1, isRead: 1, createdAt: -1 },
      { name: 'unread_messages_index' }
    );
    
    // Índice para buscar última mensagem de cada conversa
    await messagesCollection.createIndex(
      { senderId: 1, receiverId: 1, createdAt: -1 },
      { name: 'last_message_index' }
    );
    
    logger.info({ message: 'MongoDB indexes created successfully' });
  } catch (error) {
    logger.error({ message: 'Failed to create indexes', error: error.message });
    throw error;
  }
}

async function disconnectMongoDB() {
  try {
    if (client) {
      await client.close();
      client = null;
      db = null;
      logger.info({ message: 'MongoDB disconnected' });
    }
  } catch (error) {
    logger.error({ message: 'Error disconnecting MongoDB', error: error.message });
    throw error;
  }
}

function getDB() {
  if (!db) {
    throw new Error('MongoDB not connected. Call connectMongoDB() first.');
  }
  return db;
}

module.exports = {
  connectMongoDB,
  disconnectMongoDB,
  getDB
};
