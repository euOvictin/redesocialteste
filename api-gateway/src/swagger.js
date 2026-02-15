/**
 * OpenAPI 3.0 / Swagger documentation
 * Requirement: 15.1
 */
const swaggerUi = require('swagger-ui-express');

const specs = {
  openapi: '3.0.0',
  info: {
    title: 'Rede Social Brasileira API',
    version: '1.0.0',
    description: 'API pública para Rede Social Brasileira',
  },
  servers: [{ url: '/api/v1', description: 'API v1' }],
  paths: {
    '/auth/register': {
      post: {
        summary: 'Registrar novo usuário',
        requestBody: {
          content: {
            'application/json': {
              schema: {
                type: 'object',
                properties: { email: { type: 'string' }, password: { type: 'string' }, name: { type: 'string' } },
              },
            },
          },
        },
        responses: { 201: { description: 'Usuário criado' } },
      },
    },
    '/auth/login': {
      post: {
        summary: 'Login',
        requestBody: {
          content: {
            'application/json': {
              schema: {
                type: 'object',
                properties: { email: { type: 'string' }, password: { type: 'string' } },
              },
            },
          },
        },
        responses: { 200: { description: 'Login realizado' } },
      },
    },
    '/lgpd/export': {
      get: {
        summary: 'Exportar dados do usuário (LGPD)',
        security: [{ bearerAuth: [] }],
        responses: { 200: { description: 'Dados exportados em JSON' } },
      },
    },
    '/lgpd/account': {
      delete: {
        summary: 'Solicitar exclusão de conta (LGPD)',
        security: [{ bearerAuth: [] }],
        responses: { 200: { description: 'Solicitação recebida' } },
      },
    },
  },
  components: {
    securitySchemes: {
      bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' },
    },
  },
};

module.exports = { swaggerUi, specs };
