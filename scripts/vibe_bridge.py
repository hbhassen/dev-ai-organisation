"""Run the packaged Vibe model adapter as a bounded CLI without interactive configuration or native tools. Exigences : DEVAI-170, DEVAI-171, DEVAI-176."""
import asyncio
import json
import logging
import os
from pathlib import Path
import sys
import tempfile
from urllib.parse import urlsplit


async def complete(request, backend_factory=None):
    """Use Vibe's actual Mistral adapter and preserve ODS messages, typed tools and measured usage. Exigences : DEVAI-170, DEVAI-171, DEVAI-177."""
    from vibe.core.config import ProviderConfig, ModelConfig
    from vibe.core.llm.backend.factory import create_backend
    from vibe.core.types import LLMMessage, AvailableTool
    url = urlsplit(request["url"])
    if url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment:
        raise ValueError("Invalid managed Vibe endpoint")
    if not request.get("credential") or "<" in request["credential"]:
        raise ValueError("Missing managed Vibe credential")
    os.environ["ODS_VIBE_CREDENTIAL"] = request["credential"]
    provider = ProviderConfig(name="ods-mistral", api_base=request["url"], api_key_env_var="ODS_VIBE_CREDENTIAL", backend="mistral", browser_auth_base_url="", browser_auth_api_base_url="")
    model = ModelConfig(name=request["model"], provider="ods-mistral", alias="ods-approved")
    factory = backend_factory or create_backend
    async with factory(provider=provider, timeout=120, retry_max_elapsed_time=0, enable_otel=False) as backend:
        result = await backend.complete(model=model, messages=[LLMMessage.model_validate(item) for item in request["messages"]], temperature=0.2, tools=[AvailableTool.model_validate(item) for item in request.get("tools", [])] or None, max_tokens=min(int(request["maxOutput"]), 4000), tool_choice="auto" if request.get("tools") else None, extra_headers=None)
    if result.usage is None or result.usage.prompt_tokens <= 0 or result.usage.completion_tokens < 0 or result.stop is None or result.stop.reason not in ("stop", "tool_calls", "length"):
        raise ValueError("Incomplete Vibe response")
    message = result.message.model_dump(exclude_none=True, include={"role", "content", "tool_calls", "name", "tool_call_id"}, mode="json")
    usage = result.usage.model_dump()
    usage["total_tokens"] = usage["prompt_tokens"] + usage["completion_tokens"]
    return {"choices": [{"message": message, "finish_reason": result.stop.reason}], "usage": usage}


def main():
    """Read one private stdin frame and emit only a structured result or a secret-free failure. Exigences : DEVAI-170, DEVAI-176."""
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
        code = "VIBE_RUNTIME_UNAVAILABLE" if isinstance(error, ImportError) else "VIBE_REQUEST_FAILED"
        if status in (401, 403):
            code = "MODEL_AUTHENTICATION_FAILED"
        elif status == 429:
            code = "MODEL_RATE_LIMITED"
        elif status == 404 or getattr(error, "is_invalid_model", False):
            code = "MODEL_UNAVAILABLE"
        sys.stdout.write(json.dumps({"error": {"code": code}}))
        sys.stderr.write("Managed Vibe request failed; verify packaged runtime and organisation configuration.\n")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
