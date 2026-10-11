import base64
import binascii
import io
import os
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field
from huggingface_hub import InferenceClient

app = FastAPI(title="AI Video Creator API", version="1.0.0")

HF_TOKEN = os.getenv("HF_TOKEN", "").strip()
TEXT_MODEL = os.getenv("HF_TEXT_MODEL", "Wan-AI/Wan2.1-T2V-1.3B").strip()
IMAGE_MODEL = os.getenv("HF_IMAGE_MODEL", "Wan-AI/Wan2.2-I2V-A14B").strip()
MAX_IMAGE_MB = int(os.getenv("MAX_IMAGE_MB", "10"))
ALLOWED_SECONDS = set(range(5, 61, 5))


class TextVideoRequest(BaseModel):
    prompt: str = Field(min_length=3, max_length=2000)
    duration_seconds: int = Field(default=5, ge=5, le=60)
    negative_prompt: Optional[str] = Field(default=None, max_length=1000)
    seed: Optional[int] = None


class ImageVideoRequest(TextVideoRequest):
    image_base64: str = Field(min_length=20)


def client() -> InferenceClient:
    if not HF_TOKEN:
        raise HTTPException(
            status_code=503,
            detail="Backend sem HF_TOKEN. Configure o token como variável de ambiente no servidor."
        )
    return InferenceClient(api_key=HF_TOKEN, timeout=600)


def validate_duration(seconds: int) -> int:
    if seconds not in ALLOWED_SECONDS:
        raise HTTPException(status_code=400, detail="Duração deve ser 5, 10, 15, ... ou 60 segundos.")
    # Estimativa de 8 fps. O modelo/provedor pode limitar ou rejeitar esse número de quadros.
    return seconds * 8


def provider_error(exc: Exception) -> HTTPException:
    message = str(exc)
    # Evita devolver headers/token por acidente; a exceção comum não deveria conter o token,
    # mas a resposta ao cliente é deliberadamente resumida.
    return HTTPException(
        status_code=502,
        detail=("Falha no provedor/modelo Hugging Face. Verifique o modelo, provedor habilitado, "
                "permissões, cotas e limites de duração. Detalhe: " + message[:500])
    )


@app.get("/health")
def health():
    return {"ok": True, "service": "AI Video Creator API"}


@app.post("/generate/text")
def generate_text(req: TextVideoRequest):
    frames = validate_duration(req.duration_seconds)
    hf = client()
    try:
        kwargs = {
            "prompt": req.prompt,
            "model": TEXT_MODEL,
            "num_frames": frames,
        }
        if req.negative_prompt:
            kwargs["negative_prompt"] = [req.negative_prompt]
        if req.seed is not None:
            kwargs["seed"] = req.seed
        video = hf.text_to_video(**kwargs)
        if not video:
            raise RuntimeError("O provedor retornou um vídeo vazio.")
        return Response(content=video, media_type="video/mp4",
                        headers={"Content-Disposition": 'attachment; filename="ai-video.mp4"'})
    except HTTPException:
        raise
    except Exception as exc:
        raise provider_error(exc)


@app.post("/generate/image")
def generate_image(req: ImageVideoRequest):
    frames = validate_duration(req.duration_seconds)
    try:
        raw = req.image_base64
        if "," in raw and raw.lstrip().startswith("data:"):
            raw = raw.split(",", 1)[1]
        image_bytes = base64.b64decode(raw, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=400, detail="A imagem enviada não está em base64 válido.")

    if len(image_bytes) > MAX_IMAGE_MB * 1024 * 1024:
        raise HTTPException(status_code=413, detail=f"Imagem muito grande. Limite: {MAX_IMAGE_MB} MB.")

    hf = client()
    try:
        kwargs = {
            "image": image_bytes,
            "model": IMAGE_MODEL,
            "prompt": req.prompt,
            "num_frames": frames,
        }
        if req.negative_prompt:
            kwargs["negative_prompt"] = req.negative_prompt
        if req.seed is not None:
            kwargs["seed"] = req.seed
        video = hf.image_to_video(**kwargs)
        if not video:
            raise RuntimeError("O provedor retornou um vídeo vazio.")
        return Response(content=video, media_type="video/mp4",
                        headers={"Content-Disposition": 'attachment; filename="ai-video.mp4"'})
    except HTTPException:
        raise
    except Exception as exc:
        raise provider_error(exc)
