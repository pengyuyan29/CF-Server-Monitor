// Public test fixture only. Same crypto operations as blog/scripts/build.mjs.
const { pbkdf2Sync, createCipheriv, createHash } = require('node:crypto');
const { writeFileSync } = require('node:fs');
const salt = Buffer.alloc(32, 19);
const iv = Buffer.alloc(12, 7);
const password = '测试päss🔐';
const key = pbkdf2Sync(password, salt, 600000, 32, 'sha256');
const cipher = createCipheriv('aes-256-gcm', key, iv);
const data = Buffer.concat([cipher.update(JSON.stringify({ posts: [{ slug: '原生-测试', title: '原生阅读兼容' }] }), 'utf8'), cipher.final(), cipher.getAuthTag()]);
writeFileSync(process.argv[2], JSON.stringify({ version: 1, kdf: 'PBKDF2-SHA256', iterations: 600000, salt: salt.toString('base64'), iv: iv.toString('base64'), data: data.toString('base64'), expectedFile: createHash('sha256').update('原生-测试').digest('hex').slice(0, 16) }));
