"""Exercise the bundled Vibe implementation with an HTTP transport double instead of any real Mistral service. Exigences : DEVAI-170, DEVAI-171, DEVAI-177."""
import asyncio
import importlib.util
import json
from pathlib import Path
import sys
from unittest.mock import patch


async def verify(runtime):
    """Run the actual Vibe backend and SDK against a deterministic HTTP response and inspect the outgoing contract. Exigences : DEVAI-170, DEVAI-177."""
    sys.path.insert(0, str(runtime / "vibe"))
    spec = importlib.util.spec_from_file_location("vibe_bridge", runtime / "vibe_bridge.py")
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    import httpx
    import httpx2
    requests = []

    async def respond(request):
        """Supply a genuine Mistral chat-completion shape while refusing any unexpected destination. Exigences : DEVAI-177."""
        requests.append(request)
        assert str(request.url) == "https://mistral.invalid/v1/chat/completions"
        assert request.headers["authorization"] == "Bearer synthetic-vibe-contract"
        payload = json.loads(request.content)
        assert payload["model"] == "approved-test-model"
        assert payload["messages"][-1]["content"] == "Inspect synthetic project"
        message = {"role": "assistant", "content": "Mocked Vibe answer"}
        finish = "stop"
        if payload.get("tools"):
            assert payload["tools"][0]["function"]["name"] == "files_read"
            message = {"role": "assistant", "content": "", "tool_calls": [{"id": "call00001", "type": "function", "function": {"name": "files_read", "arguments": '{"path":"pom.xml"}'}}]}
            finish = "tool_calls"
        return httpx.Response(200, json={"id": "synthetic-completion", "object": "chat.completion", "created": 1, "model": "approved-test-model", "choices": [{"index": 0, "message": message, "finish_reason": finish}], "usage": {"prompt_tokens": 11, "completion_tokens": 5, "total_tokens": 16}}, request=request)

    async def send(client, request, *args, **kwargs):
        """Replace only the SDK network boundary; Vibe serialization and response parsing remain real. Exigences : DEVAI-177."""
        return await respond(request)

    with patch.object(httpx.AsyncClient, "send", new=send), patch.object(httpx2.AsyncClient, "send", new=send):
        request = {"url": "https://mistral.invalid/v1", "model": "approved-test-model", "credential": "synthetic-vibe-contract", "messages": [{"role": "user", "content": "Inspect synthetic project"}], "tools": [], "maxOutput": 100}
        result = await bridge.complete(request)
        tool_result = await bridge.complete(request | {"tools": [{"type": "function", "function": {"name": "files_read", "description": "Read an approved file", "parameters": {"type": "object", "properties": {"path": {"type": "string"}}, "required": ["path"]}}}]})
    assert len(requests) == 2
    assert result["choices"][0]["message"]["content"] == "Mocked Vibe answer"
    assert result["usage"]["total_tokens"] == 16
    call = tool_result["choices"][0]["message"]["tool_calls"][0]
    assert call["function"]["name"] == "files_read" and json.loads(call["function"]["arguments"]) == {"path": "pom.xml"}
    assert tool_result["choices"][0]["finish_reason"] == "tool_calls"
    print("Vibe packaged backend + SDK + mocked HTTP: SUCCESS")


def main():
    """Require an explicit packaged runtime directory so the test cannot accidentally use a user Vibe installation. Exigences : DEVAI-171, DEVAI-177."""
    if len(sys.argv) != 2:
        raise ValueError("Packaged runtime directory required")
    asyncio.run(verify(Path(sys.argv[1]).resolve()))


if __name__ == "__main__":
    main()
