-- Inicialização do PostgreSQL para Rede Social Brasileira

-- Criar extensões necessárias
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Criar schema para User Service
CREATE SCHEMA IF NOT EXISTS user_service;

-- Tabela de usuários
CREATE TABLE IF NOT EXISTS user_service.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    bio TEXT,
    profile_picture_url TEXT,
    followers_count INTEGER DEFAULT 0,
    following_count INTEGER DEFAULT 0,
    is_private BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Índices para users
CREATE INDEX IF NOT EXISTS idx_users_email ON user_service.users(email);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON user_service.users(created_at);

-- Tabela de relacionamentos (seguir/seguidores)
CREATE TABLE IF NOT EXISTS user_service.followers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    follower_id UUID NOT NULL REFERENCES user_service.users(id) ON DELETE CASCADE,
    following_id UUID NOT NULL REFERENCES user_service.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(follower_id, following_id),
    CHECK (follower_id != following_id)
);

-- Índices para followers
CREATE INDEX IF NOT EXISTS idx_followers_follower_id ON user_service.followers(follower_id);
CREATE INDEX IF NOT EXISTS idx_followers_following_id ON user_service.followers(following_id);
CREATE INDEX IF NOT EXISTS idx_followers_created_at ON user_service.followers(created_at);

-- Criar schema para Content Service
CREATE SCHEMA IF NOT EXISTS content_service;

-- Tabela de metadados de posts
CREATE TABLE IF NOT EXISTS content_service.post_metadata (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('text', 'image', 'video', 'mixed')),
    likes_count INTEGER DEFAULT 0,
    comments_count INTEGER DEFAULT 0,
    shares_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Índices para post_metadata
CREATE INDEX IF NOT EXISTS idx_post_metadata_user_id ON content_service.post_metadata(user_id);
CREATE INDEX IF NOT EXISTS idx_post_metadata_created_at ON content_service.post_metadata(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_post_metadata_deleted_at ON content_service.post_metadata(deleted_at);

-- Tabela de curtidas
CREATE TABLE IF NOT EXISTS content_service.likes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    post_id UUID NOT NULL REFERENCES content_service.post_metadata(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(post_id, user_id)
);

-- Índices para likes
CREATE INDEX IF NOT EXISTS idx_likes_post_id ON content_service.likes(post_id);
CREATE INDEX IF NOT EXISTS idx_likes_user_id ON content_service.likes(user_id);

-- Criar schema para Recommendation Engine
CREATE SCHEMA IF NOT EXISTS recommendation_service;

-- Tabela de interações para treinamento
CREATE TABLE IF NOT EXISTS recommendation_service.user_interactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    post_id UUID NOT NULL,
    interaction_type VARCHAR(20) NOT NULL CHECK (interaction_type IN ('view', 'like', 'comment', 'share')),
    interaction_value FLOAT DEFAULT 1.0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índices para user_interactions
CREATE INDEX IF NOT EXISTS idx_interactions_user_id ON recommendation_service.user_interactions(user_id);
CREATE INDEX IF NOT EXISTS idx_interactions_post_id ON recommendation_service.user_interactions(post_id);
CREATE INDEX IF NOT EXISTS idx_interactions_created_at ON recommendation_service.user_interactions(created_at DESC);

-- Função para atualizar updated_at automaticamente
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers para atualizar updated_at
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON user_service.users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_post_metadata_updated_at BEFORE UPDATE ON content_service.post_metadata
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Inserir dados de exemplo para desenvolvimento (opcional)
-- Comentar em produção
INSERT INTO user_service.users (email, password_hash, name, bio) VALUES
    ('admin@redesocial.com.br', '$2b$10$YourHashedPasswordHere', 'Admin', 'Administrador da plataforma'),
    ('user1@example.com', '$2b$10$YourHashedPasswordHere', 'João Silva', 'Desenvolvedor apaixonado por tecnologia'),
    ('user2@example.com', '$2b$10$YourHashedPasswordHere', 'Maria Santos', 'Designer e criadora de conteúdo')
ON CONFLICT (email) DO NOTHING;

-- Mensagem de sucesso
DO $$
BEGIN
    RAISE NOTICE 'PostgreSQL inicializado com sucesso para Rede Social Brasileira!';
END $$;
