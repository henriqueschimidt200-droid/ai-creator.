# AI Video Creator — Android APK + Hugging Face

Projeto inicial de aplicativo Android nativo em Kotlin (sem Flutter) com geração de vídeo por texto ou por imagem. O token Hugging Face fica no backend, nunca dentro do APK.

## Sobre durações de 5 a 60 segundos
O app permite escolher 5, 10, 15, ... 60 segundos. Essa seleção é uma solicitação, não uma garantia: os modelos e provedores podem impor limites de quadros/duração, e muitos geram apenas clipes curtos. O backend converte a duração solicitada em `num_frames` (8 fps como estimativa), mas o provedor/modelo pode rejeitar o pedido ou devolver uma duração diferente. Para vídeos longos, normalmente é preciso gerar vários clipes e juntá-los; isso ainda não é automático neste protótipo.

## Estrutura
- `android/`: app Android nativo.
- `backend/`: API Python/FastAPI.
- `.github/workflows/android-apk.yml`: workflow para compilar APK de teste.
- `backend/.env.example`: exemplo das variáveis.

## 1. Criar token HF
1. Acesse https://huggingface.co/settings/tokens
2. Crie um token com permissões para Inference Providers.
3. Não coloque o token em Kotlin, no APK, nem em repositório público.

Docs úteis:
- https://huggingface.co/docs/inference-providers/tasks/text-to-video
- https://huggingface.co/docs/huggingface_hub/main/package_reference/inference_client

## 2. Iniciar o backend localmente para testar
Requer Python 3.10+.

Windows:
```powershell
cd backend
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:HF_TOKEN="hf_SEU_TOKEN"
$env:HF_TEXT_MODEL="Wan-AI/Wan2.1-T2V-1.3B"
$env:HF_IMAGE_MODEL="Wan-AI/Wan2.2-I2V-A14B"
uvicorn app:app --host 0.0.0.0 --port 8000
```

Linux/macOS:
```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export HF_TOKEN="hf_SEU_TOKEN"
export HF_TEXT_MODEL="Wan-AI/Wan2.1-T2V-1.3B"
export HF_IMAGE_MODEL="Wan-AI/Wan2.2-I2V-A14B"
uvicorn app:app --host 0.0.0.0 --port 8000
```

Os IDs acima são exemplos a confirmar. Verifique no Hub se o modelo tem provedor compatível ativo e acesso liberado na sua conta. Os provedores/modelos disponíveis podem mudar. Alguns modelos exigem um provedor específico; ajuste os IDs e o código conforme a documentação do modelo.

## 3. Conectar o app ao backend
- Celular e computador na mesma rede Wi-Fi: no campo “URL do servidor”, use o IP local do computador, por exemplo `http://192.168.1.20:8000`.
- Em produção, hospede o backend em um servidor HTTPS e restrinja o acesso com autenticação e limites de uso. Não exponha a API sem proteção na internet.
- `http://10.0.2.2:8000` funciona para o emulador Android acessar o computador host.

## 4. Gerar o APK no GitHub
1. Extraia o ZIP.
2. Envie todos os arquivos e pastas para um repositório GitHub.
3. Abra **Actions**.
4. Execute **Build Android APK** usando **Run workflow**, ou faça push para `main`.
5. Abra a execução finalizada e baixe o artefato `AI-Video-Creator-debug-apk`.
6. Extraia o artefato ZIP e instale `app-debug.apk` no Android. Talvez seja necessário permitir instalação dessa origem.

O workflow gera um APK de depuração para teste, não um APK assinado para publicar na Play Store.

## Limites e custos
Hugging Face Inference Providers não garante geração ilimitada/gratuita. Pode haver cotas, filas, cobrança e falhas por falta de provedor disponível. O vídeo é processado na nuvem; o celular não roda o modelo pesado.
