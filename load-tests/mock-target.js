#!/usr/bin/env node
const http = require('http');

const PORT = process.env.MOCK_PORT || 9099;
let count = 0;

const server = http.createServer((req, res) => {
  count++;
  req.on('data', () => {});
  req.on('end', () => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end('{"status":"ok","upstream":"mock-target"}');
  });
});

server.keepAliveTimeout = 30000;
server.headersTimeout   = 31000;

server.listen(PORT, () => {
  console.log(`mock-target listening on :${PORT}`);
});

if (process.env.MOCK_VERBOSE === '1') {
  setInterval(() => {
    if (count > 0) console.log(`mock-target served ${count} requests`);
  }, 5000).unref();
}

process.on('SIGTERM', () => server.close(() => process.exit(0)));
process.on('SIGINT',  () => server.close(() => process.exit(0)));
