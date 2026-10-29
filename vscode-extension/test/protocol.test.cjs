const { test } = require('node:test');
const assert = require('node:assert/strict');
const { repositoryPath, validateMessage } = require('../dist/protocol');
test('DEVAI-025: HTTPS and SSH remotes resolve canonical paths', () => { assert.equal(repositoryPath('https://gitlab.example/team/project.git'), 'team/project'); assert.equal(repositoryPath('git@gitlab.example:team/project.git'), 'team/project'); });
test('DEVAI-025: credential-bearing and unsupported remotes are rejected', () => { assert.throws(() => repositoryPath('https://user:password@gitlab.example/project.git')); assert.throws(() => repositoryPath('file:///project')); });
test('DEVAI-018: only fixed native actions can cross the webview bridge', () => { assert.equal(validateMessage({ type: 'chat', mode: 'AGENT' }).type, 'chat'); assert.throws(() => validateMessage({ type: 'eval', code: 'anything' })); assert.throws(() => validateMessage([])); });
test('DEVAI-018: oversized payloads are rejected before processing', () => { assert.throws(() => validateMessage({ type: 'chat', message: 'x'.repeat(100001) })); });
