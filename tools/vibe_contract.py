"""Exercise the bundled Vibe implementation with an HTTP transport double instead of any real Mistral service. Exigences : DEVAI-170, DEVAI-171, DEVAI-177."""
import asyncio
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
from unittest.mock import patch


def cli_case(runtime, status):
    """Exercise the packaged entry point in a fresh process, mocking only HTTP and preserving its Windows cleanup. Exigences : DEVAI-170, DEVAI-177."""
    library = str(runtime / "vibe")
    if os.name == "nt" and not library.startswith("\\\\?\\"):
        library = "\\\\?\\UNC\\" + library[2:] if library.startswith("\\\\") else "\\\\?\\" + library
    sys.path.insert(0, library)
    spec = importlib.util.spec_from_file_location("vibe_bridge", runtime / "vibe_bridge.py")
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    import httpx
    import httpx2
    calls = []

    async def send(client, request, *args, **kwargs):
        """Reject unexpected destinations and return only synthetic SDK-compatible responses without network access. Exigences : DEVAI-176, DEVAI-177."""
        assert str(request.url) == "https://mistral.invalid/v1/chat/completions"
        assert request.headers["authorization"] == "Bearer synthetic-vibe-contract"
        assert json.loads(request.content)["model"] == "mistral-medium-3-5"
        calls.append(request)
        body = {"message": "synthetic-private-provider-detail", "type": "invalid_request_error", "code": "invalid_model"}
        if status == 200:
            body = {"id": "synthetic-completion", "object": "chat.completion", "created": 1, "model": "mistral-medium-3-5", "choices": [{"index": 0, "message": {"role": "assistant", "content": "Synthetic CLI answer"}, "finish_reason": "stop"}], "usage": {"prompt_tokens": 11, "completion_tokens": 5, "total_tokens": 16}}
        return httpx.Response(status, json=body, request=request)

    before = Path.cwd()
    with patch.object(httpx.AsyncClient, "send", new=send), patch.object(httpx2.AsyncClient, "send", new=send):
        result = bridge.main()
    assert len(calls) == 1 and Path.cwd() == before
    assert not Path(os.environ["VIBE_HOME"]).exists()
    return result


def verify_cli(runtime):
    """Check success and real SDK error classification through isolated Python stdin/stdout frames. Exigences : DEVAI-170, DEVAI-176, DEVAI-177."""
    request = {"url": "https://mistral.invalid/v1", "model": "mistral-medium-3-5", "credential": "synthetic-vibe-contract", "messages": [{"role": "user", "content": "Inspect synthetic project"}], "tools": [], "maxOutput": 100}
    environment = {key: value for key, value in os.environ.items() if key.upper() in ("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "HOME", "USERPROFILE", "LANG", "APPDATA", "LOCALAPPDATA")}
    cases = {200: None, 401: "MODEL_AUTHENTICATION_FAILED", 429: "MODEL_RATE_LIMITED", 404: "MODEL_UNAVAILABLE", 400: "MODEL_UNAVAILABLE", 500: "VIBE_REQUEST_FAILED"}
    for status, expected in cases.items():
        child = subprocess.run([sys.executable, "-I", str(Path(__file__).resolve()), str(runtime), "--cli-case", str(status)], input=json.dumps(request), capture_output=True, text=True, env=environment, timeout=60)
        assert "synthetic-private-provider-detail" not in child.stdout + child.stderr
        assert child.returncode == (0 if status == 200 else 1), f"CLI case {status} failed"
        response = json.loads(child.stdout)
        if expected:
            assert response == {"error": {"code": expected}}, f"CLI classification {status} failed"
        else:
            assert response["choices"][0]["message"]["content"] == "Synthetic CLI answer"
            assert response["usage"]["total_tokens"] == 16
    print("Vibe packaged CLI lifecycle + success/auth/quota/model/server errors: 6 cases SUCCESS (zero real API calls)")


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
    if len(sys.argv) == 4 and sys.argv[2] == "--cli-case":
        return cli_case(Path(sys.argv[1]).resolve(), int(sys.argv[3]))
    if len(sys.argv) != 2:
        raise ValueError("Packaged runtime directory required")
    asyncio.run(verify(Path(sys.argv[1]).resolve()))
    verify_cli(Path(sys.argv[1]).resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
