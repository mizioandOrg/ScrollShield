FROM python:3.11-slim-bookworm

RUN groupadd -r apiuser && useradd -r -g apiuser -m -d /home/apiuser apiuser

WORKDIR /app

COPY docker/signature-api/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY docker/signature-api/main.py main.py

RUN chown -R apiuser:apiuser /app

USER apiuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8080/health')" || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080"]
