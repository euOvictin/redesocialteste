// Inicialização do MongoDB para Rede Social Brasileira

// Conectar ao banco de dados
db = db.getSiblingDB('rede_social');

// Criar coleções com validação de schema

// Coleção de posts
db.createCollection('posts', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['user_id', 'content', 'created_at'],
      properties: {
        user_id: {
          bsonType: 'string',
          description: 'ID do usuário que criou o post'
        },
        content: {
          bsonType: 'string',
          minLength: 1,
          maxLength: 5000,
          description: 'Conteúdo do post (1-5000 caracteres)'
        },
        media_urls: {
          bsonType: 'array',
          description: 'URLs de mídia (imagens/vídeos)',
          items: {
            bsonType: 'object',
            required: ['url', 'type'],
            properties: {
              url: { bsonType: 'string' },
              type: { enum: ['image', 'video'] },
              thumbnail_url: { bsonType: 'string' },
              width: { bsonType: 'int' },
              height: { bsonType: 'int' },
              duration: { bsonType: 'int' }
            }
          }
        },
        hashtags: {
          bsonType: 'array',
          description: 'Lista de hashtags extraídas',
          items: { bsonType: 'string' }
        },
        shared_post_id: {
          bsonType: 'string',
          description: 'ID do post original se for compartilhamento'
        },
        created_at: {
          bsonType: 'date',
          description: 'Data de criação'
        },
        updated_at: {
          bsonType: 'date',
          description: 'Data de atualização'
        }
      }
    }
  }
});

// Índices para posts
db.posts.createIndex({ user_id: 1, created_at: -1 });
db.posts.createIndex({ hashtags: 1 });
db.posts.createIndex({ created_at: -1 });
db.posts.createIndex({ 'content': 'text' });

// Coleção de comentários
db.createCollection('comments', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['post_id', 'user_id', 'content', 'created_at'],
      properties: {
        post_id: {
          bsonType: 'string',
          description: 'ID do post comentado'
        },
        user_id: {
          bsonType: 'string',
          description: 'ID do usuário que comentou'
        },
        content: {
          bsonType: 'string',
          minLength: 1,
          maxLength: 1000,
          description: 'Conteúdo do comentário (1-1000 caracteres)'
        },
        likes_count: {
          bsonType: 'int',
          minimum: 0,
          description: 'Contador de curtidas'
        },
        created_at: {
          bsonType: 'date',
          description: 'Data de criação'
        },
        updated_at: {
          bsonType: 'date',
          description: 'Data de atualização'
        }
      }
    }
  }
});

// Índices para comments
db.comments.createIndex({ post_id: 1, created_at: -1 });
db.comments.createIndex({ user_id: 1 });

// Coleção de stories
db.createCollection('stories', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['user_id', 'media_url', 'created_at', 'expires_at'],
      properties: {
        user_id: {
          bsonType: 'string',
          description: 'ID do usuário que criou o story'
        },
        media_url: {
          bsonType: 'object',
          required: ['url', 'type'],
          properties: {
            url: { bsonType: 'string' },
            type: { enum: ['image', 'video'] },
            thumbnail_url: { bsonType: 'string' },
            width: { bsonType: 'int' },
            height: { bsonType: 'int' },
            duration: { bsonType: 'int' }
          }
        },
        views_count: {
          bsonType: 'int',
          minimum: 0,
          description: 'Contador de visualizações'
        },
        created_at: {
          bsonType: 'date',
          description: 'Data de criação'
        },
        expires_at: {
          bsonType: 'date',
          description: 'Data de expiração (24 horas após criação)'
        }
      }
    }
  }
});

// Índices para stories
db.stories.createIndex({ user_id: 1, created_at: -1 });
db.stories.createIndex({ expires_at: 1 }, { expireAfterSeconds: 0 }); // TTL index

// Coleção de visualizações de stories
db.createCollection('story_views', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['story_id', 'viewer_id', 'viewed_at'],
      properties: {
        story_id: {
          bsonType: 'string',
          description: 'ID do story visualizado'
        },
        viewer_id: {
          bsonType: 'string',
          description: 'ID do usuário que visualizou'
        },
        viewed_at: {
          bsonType: 'date',
          description: 'Data de visualização'
        }
      }
    }
  }
});

// Índices para story_views
db.story_views.createIndex({ story_id: 1, viewed_at: -1 });
db.story_views.createIndex({ viewer_id: 1 });
db.story_views.createIndex({ viewed_at: 1 }, { expireAfterSeconds: 86400 }); // Expira após 24h

// Coleção de mensagens
db.createCollection('messages', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['sender_id', 'receiver_id', 'content', 'created_at'],
      properties: {
        sender_id: {
          bsonType: 'string',
          description: 'ID do remetente'
        },
        receiver_id: {
          bsonType: 'string',
          description: 'ID do destinatário'
        },
        content: {
          bsonType: 'string',
          minLength: 1,
          maxLength: 5000,
          description: 'Conteúdo da mensagem (1-5000 caracteres)'
        },
        media_url: {
          bsonType: 'string',
          description: 'URL de mídia anexada (opcional)'
        },
        is_read: {
          bsonType: 'bool',
          description: 'Indica se a mensagem foi lida'
        },
        delivered_at: {
          bsonType: 'date',
          description: 'Data de entrega'
        },
        read_at: {
          bsonType: 'date',
          description: 'Data de leitura'
        },
        created_at: {
          bsonType: 'date',
          description: 'Data de criação'
        }
      }
    }
  }
});

// Índices para messages
db.messages.createIndex({ sender_id: 1, receiver_id: 1, created_at: -1 });
db.messages.createIndex({ receiver_id: 1, is_read: 1 });

// Coleção de notificações
db.createCollection('notifications', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['user_id', 'type', 'content', 'created_at'],
      properties: {
        user_id: {
          bsonType: 'string',
          description: 'ID do usuário que recebe a notificação'
        },
        type: {
          enum: ['like', 'comment', 'follow', 'mention', 'message'],
          description: 'Tipo de notificação'
        },
        actor_id: {
          bsonType: 'string',
          description: 'ID do usuário que gerou a notificação'
        },
        target_id: {
          bsonType: 'string',
          description: 'ID do recurso relacionado (post, comment, etc)'
        },
        content: {
          bsonType: 'string',
          description: 'Conteúdo da notificação'
        },
        is_read: {
          bsonType: 'bool',
          description: 'Indica se a notificação foi lida'
        },
        created_at: {
          bsonType: 'date',
          description: 'Data de criação'
        }
      }
    }
  }
});

// Índices para notifications
db.notifications.createIndex({ user_id: 1, created_at: -1 });
db.notifications.createIndex({ user_id: 1, is_read: 1 });
db.notifications.createIndex({ created_at: 1 }, { expireAfterSeconds: 7776000 }); // Expira após 90 dias

// Inserir dados de exemplo para desenvolvimento (opcional)
db.posts.insertMany([
  {
    user_id: 'user-1',
    content: 'Bem-vindo à Rede Social Brasileira! 🇧🇷 #bemvindo #redesocial',
    media_urls: [],
    hashtags: ['bemvindo', 'redesocial'],
    created_at: new Date(),
    updated_at: new Date()
  },
  {
    user_id: 'user-2',
    content: 'Primeiro post de teste com imagem! #teste',
    media_urls: [
      {
        url: 'https://example.com/image.jpg',
        type: 'image',
        thumbnail_url: 'https://example.com/image_thumb.jpg',
        width: 1920,
        height: 1080
      }
    ],
    hashtags: ['teste'],
    created_at: new Date(),
    updated_at: new Date()
  }
]);

print('MongoDB inicializado com sucesso para Rede Social Brasileira!');
print('Coleções criadas: posts, comments, stories, story_views, messages, notifications');
