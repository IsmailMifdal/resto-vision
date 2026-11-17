# Image du simulateur : rejoue un service de restaurant, sans caméra ni modèle de vision.
FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir "paho-mqtt>=2.1" "cryptography>=43"
COPY restovision_edge restovision_edge
COPY config config
ENV PYTHONUNBUFFERED=1
ENTRYPOINT ["python", "-m", "restovision_edge", "--config", "config/edge.example.json", "--source", "simulate", "--publisher", "mqtt"]
