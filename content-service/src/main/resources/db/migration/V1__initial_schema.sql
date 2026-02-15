-- Post Metadata Table
CREATE TABLE post_metadata (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('TEXT', 'IMAGE', 'VIDEO', 'MIXED')),
    likes_count INTEGER DEFAULT 0 NOT NULL,
    comments_count INTEGER DEFAULT 0 NOT NULL,
    shares_count INTEGER DEFAULT 0 NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_post_metadata_user_id ON post_metadata(user_id);
CREATE INDEX idx_post_metadata_created_at ON post_metadata(created_at DESC);
CREATE INDEX idx_post_metadata_is_deleted ON post_metadata(is_deleted);

-- Likes Table
CREATE TABLE likes (
    id BIGSERIAL PRIMARY KEY,
    post_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(post_id, user_id)
);

CREATE INDEX idx_likes_post_id ON likes(post_id);
CREATE INDEX idx_likes_user_id ON likes(user_id);

-- Shares Table
CREATE TABLE shares (
    id BIGSERIAL PRIMARY KEY,
    original_post_id VARCHAR(36) NOT NULL,
    shared_post_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shares_original_post_id ON shares(original_post_id);
CREATE INDEX idx_shares_user_id ON shares(user_id);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger for post_metadata
CREATE TRIGGER update_post_metadata_updated_at
    BEFORE UPDATE ON post_metadata
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
