"""Test-only launcher for the unmodified bridge with synthetic HTTP responses; never used in the delivered VSIX."""
import importlib.util
import json
import os
from pathlib import Path
import sys
from unittest.mock import patch

RUNTIME = Path(__file__).resolve().parent
LIBRARY = str(RUNTIME / 'vibe')
if os.name == 'nt':
    LIBRARY = '\\\\?\\UNC\\' + LIBRARY[2:] if LIBRARY.startswith('\\\\') else '\\\\?\\' + LIBRARY
sys.path.insert(0, LIBRARY)
import httpx
import httpx2


async def send(client, request, *args, **kwargs):
    assert str(request.url) == 'https://mistral.invalid/v1/chat/completions'
    assert request.headers['authorization'] == 'Bearer synthetic-profile-test'
    payload = json.loads(request.content)
    assert payload['model'] == 'mistral-medium-3-5'
    mode = (RUNTIME / 'fixture-mode.txt').read_text().strip()
    with (RUNTIME / 'fixture-calls.jsonl').open('a') as output:
        output.write(json.dumps({'mode': mode, 'model': payload['model'], 'synthetic': True}) + '\n')
    if mode in ('401', '429', '404'):
        return httpx.Response(int(mode), json={'message': 'synthetic-private-provider-detail'}, request=request)
    message = {'role': 'assistant', 'content': 'Analyse synthetique terminee : communication front gateway Vibe validee.'}
    reason = 'stop'
    if mode == 'tool' and not any(item['role'] == 'tool' for item in payload['messages']):
        assert any(item['function']['name'] == 'files_read' for item in payload['tools'])
        message = {'role': 'assistant', 'content': '', 'tool_calls': [{'id': 'call00001', 'type': 'function', 'function': {'name': 'files_read', 'arguments': '{"path":"pom.xml"}'}}]}
        reason = 'tool_calls'
    elif mode == 'tool':
        tool = next(item for item in payload['messages'] if item['role'] == 'tool')
        assert 'project' in tool['content'] and 'sha256' in tool['content']
    return httpx.Response(200, json={'id': 'synthetic-profile', 'object': 'chat.completion', 'created': 1, 'model': payload['model'], 'choices': [{'index': 0, 'message': message, 'finish_reason': reason}], 'usage': {'prompt_tokens': 20, 'completion_tokens': 10, 'total_tokens': 30}}, request=request)


if __name__ == '__main__':
    spec = importlib.util.spec_from_file_location('vibe_bridge_actual', RUNTIME / 'vibe_bridge_actual.py')
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    with patch.object(httpx.AsyncClient, 'send', new=send), patch.object(httpx2.AsyncClient, 'send', new=send):
        raise SystemExit(bridge.main())
