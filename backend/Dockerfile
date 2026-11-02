FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app
COPY requirements.txt ./
RUN python -m pip install --no-cache-dir -r requirements.txt \
    && useradd --create-home --uid 10001 api \
    && chown api:api /app

COPY --chown=api:api . .
USER api
EXPOSE 8000

# Um processo mantém o agendador do MVP; cria tabelas novas sem apagar dados.
CMD ["sh", "-c", "python -m app.init_db && exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --workers 1"]
