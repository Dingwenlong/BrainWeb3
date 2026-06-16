# BrainWeb3 EEG service — Flask + numpy + mne (build context: ./services/eeg-service)
FROM python:3.11-slim
WORKDIR /app
ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt gunicorn
COPY . .
EXPOSE 8101
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8101/health')" || exit 1
# Flask app factory served by gunicorn
CMD ["gunicorn", "-b", "0.0.0.0:8101", "-w", "2", "--timeout", "120", "app:create_app()"]
