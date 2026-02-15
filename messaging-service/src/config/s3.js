const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');
const config = require('./index');
const logger = require('../utils/logger');

let s3Client = null;

function getS3Client() {
  if (!s3Client) {
    s3Client = new S3Client({
      region: config.s3.region,
      endpoint: config.s3.endpoint,
      credentials: {
        accessKeyId: config.s3.accessKey,
        secretAccessKey: config.s3.secretKey
      },
      forcePathStyle: true // Required for MinIO
    });
    logger.info({ message: 'S3 client initialized', endpoint: config.s3.endpoint });
  }
  return s3Client;
}

async function uploadImageToS3(buffer, contentType, userId, filename) {
  const client = getS3Client();
  const key = `messages/images/${userId}/${Date.now()}-${filename}`;

  const command = new PutObjectCommand({
    Bucket: config.s3.bucket,
    Key: key,
    Body: buffer,
    ContentType: contentType
  });

  await client.send(command);

  // Generate URL (for MinIO: endpoint/bucket/key)
  const url = `${config.s3.endpoint}/${config.s3.bucket}/${key}`;
  return url;
}

module.exports = {
  getS3Client,
  uploadImageToS3
};
