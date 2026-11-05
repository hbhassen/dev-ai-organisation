"""Run the packaged Vibe adapter for a server-resolved provider without user configuration or native tools. Exigences : DEVAI-170, DEVAI-171, DEVAI-176, DEVAI-191, DEVAI-198."""
import asyncio
from contextlib import asynccontextmanager
import json
import logging
import os
from pathlib import Path
import sys
import tempfile
from urllib.parse import urlsplit


def openai_payload(content):
    """Adapt pinned Vibe Chat Completions fields to the qualified OpenAI wire contract. Exigences : DEVAI-189, DEVAI-193, DEVAI-198."""
    payload = json.loads(content)
    payload.pop("temperature", None)
    if "max_tokens" in payload:
        payload["max_completion_tokens"] = payload.pop("max_tokens")
    if payload.get("model", "").startswith(("gpt-6-luna", "gpt-6-sol")):
        payload["reasoning_effort"] = "none"
    return json.dumps(payload, ensure_ascii=False).encode("utf8")


@asynccontextmanager
async def openai_backend(**options):
    """Keep Vibe's real generic backend with an owned TLS client and explicit OpenAI field adaptation. Exigences : DEVAI-171, DEVAI-191, DEVAI-193, DEVAI-198."""
    from vibe.core.llm.backend.generic import GenericBackend
    from vibe.utils.http import VibeAsyncHTTPClient, build_ssl_context

    class ManagedOpenAIClient(VibeAsyncHTTPClient):
        """Normalize only the managed OpenAI completion body before Vibe sends it. Exigences : DEVAI-191, DEVAI-198."""

        def build_request(self, method, url, **kwargs):
            """Preserve Vibe authentication and transport while adapting completion parameters. Exigences : DEVAI-191, DEVAI-198."""
            if kwargs.get("content") is not None:
                kwargs["content"] = openai_payload(kwargs["content"])
            return super().build_request(method, url, **kwargs)

    async with ManagedOpenAIClient(timeout=options["timeout"], verify=build_ssl_context(), follow_redirects=False) as client:
        async with GenericBackend(client=client, **options) as backend:
            yield backend


async def complete(request, backend_factory=None):
    """Use the pinned Vibe Mistral or qualified generic adapter with ODS tools and measured usage. Exigences : DEVAI-170, DEVAI-171, DEVAI-177, DEVAI-191, DEVAI-193, DEVAI-198."""
    from vibe.core.config import ProviderConfig, ModelConfig
    from vibe.core.llm.backend.factory import create_backend
    from vibe.core.types import LLMMessage, AvailableTool
    url = urlsplit(request["url"])
    if url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment:
        raise ValueError("Invalid managed Vibe endpoint")
    if not request.get("credential") or "<" in request["credential"]:
        raise ValueError("Missing managed Vibe credential")
    provider_id = request["provider"]
    backend_name = request["backend"]
    api_style = request["apiStyle"]
    max_context_tokens = int(request["maxContextTokens"])
    max_output_tokens = int(request["maxOutputTokens"])
    requested_output = int(request["maxOutput"])
    if max_context_tokens < 2 or max_output_tokens < 1 or max_output_tokens >= max_context_tokens or requested_output < 1 or requested_output > max_output_tokens:
        raise ValueError("Invalid managed model token limits")
    if not provider_id.isascii() or not provider_id.isupper() or backend_name not in ("mistral", "generic"):
        raise ValueError("Invalid managed Vibe provider")
    if backend_name == "mistral" and (provider_id != "MISTRAL" or api_style != "openai"):
        raise ValueError("Invalid managed Mistral adapter")
    if backend_name == "generic" and api_style != "openai":
        raise ValueError("Invalid managed generic adapter")
    os.environ["ODS_VIBE_CREDENTIAL"] = request["credential"]
    provider_name = "ods-" + provider_id.lower()
    provider = ProviderConfig(name=provider_name, api_base=request["url"], api_key_env_var="ODS_VIBE_CREDENTIAL", api_style=api_style, backend=backend_name, browser_auth_base_url="", browser_auth_api_base_url="")
    model = ModelConfig(name=request["model"], provider=provider_name, alias="ods-approved", max_context_length=max_context_tokens, max_output_tokens=max_output_tokens)
    factory = backend_factory or (openai_backend if backend_name == "generic" else create_backend)
    async with factory(provider=provider, timeout=120, retry_max_elapsed_time=0, enable_otel=False) as backend:
        # Vibe requires numeric telemetry even on error; the managed OpenAI client removes this field from the HTTP body.
        temperature = 0.2
        result = await backend.complete(model=model, messages=[LLMMessage.model_validate(item) for item in request["messages"]], temperature=temperature, tools=[AvailableTool.model_validate(item) for item in request.get("tools", [])] or None, max_tokens=min(requested_output, max_output_tokens, 4000), tool_choice="auto" if request.get("tools") else None, extra_headers=None)
    if result.usage is None or result.usage.prompt_tokens <= 0 or result.usage.completion_tokens < 0 or result.stop is None or result.stop.reason not in ("stop", "tool_calls", "length"):
        raise ValueError("Incomplete Vibe response")
    message = result.message.model_dump(exclude_none=True, include={"role", "content", "tool_calls", "name", "tool_call_id"}, mode="json")
    usage = result.usage.model_dump()
    usage["total_tokens"] = usage["prompt_tokens"] + usage["completion_tokens"]
    return {"choices": [{"message": message, "finish_reason": result.stop.reason}], "usage": usage}


def main():
    """Read one private stdin frame and emit only a structured result or a secret-free failure. Exigences : DEVAI-170, DEVAI-176, DEVAI-191, DEVAI-198."""
    sys.dont_write_bytecode = True
    runtime = Path(__file__).resolve().parent
    library = str(runtime / "vibe")
    if os.name == "nt" and not library.startswith("\\\\?\\"):
        library = "\\\\?\\UNC\\" + library[2:] if library.startswith("\\\\") else "\\\\?\\" + library
    sys.path.insert(0, library)
    logging.disable(logging.CRITICAL)
    try:
        payload = sys.stdin.buffer.read(8_388_609)
        if len(payload) > 8_388_608:
            raise ValueError("Oversized request")
        with tempfile.TemporaryDirectory(prefix="ods-vibe-") as isolated:
            os.environ["VIBE_HOME"] = isolated
            os.environ["OTEL_SDK_DISABLED"] = "true"
            os.environ["DO_NOT_TRACK"] = "1"
            previous_directory = Path.cwd()
            try:
                os.chdir(isolated)
                result = asyncio.run(complete(json.loads(payload)))
            finally:
                os.chdir(previous_directory)
        sys.stdout.write(json.dumps(result, ensure_ascii=True))
        return 0
    except Exception as error:
        status = getattr(error, "status", None)
        invalid_model = getattr(error, "is_invalid_model", False)
        if status == 400 and not invalid_model:
            try:
                body = getattr(error, "body_text", "")
                detail = json.loads(body).get("error", {}) if len(body) <= 8192 else {}
                invalid_model = isinstance(detail, dict) and detail.get("code") in ("model_not_found", "invalid_model")
            except (ValueError, TypeError, AttributeError):
                pass
        code = "VIBE_RUNTIME_UNAVAILABLE" if isinstance(error, ImportError) else "VIBE_REQUEST_FAILED"
        if status in (401, 403):
            code = "MODEL_AUTHENTICATION_FAILED"
        elif status == 429:
            code = "MODEL_RATE_LIMITED"
        elif status == 404 or invalid_model:
            code = "MODEL_UNAVAILABLE"
        sys.stdout.write(json.dumps({"error": {"code": code}}))
        sys.stderr.write("Managed Vibe request failed; verify packaged runtime and organisation configuration.\n")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
