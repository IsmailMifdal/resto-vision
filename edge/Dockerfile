# Image du simulateur : rejoue un service de restaurant, sans caméra ni modèle de vision.
FROM python:3.12-slim
WORKDIR /app
# Versions figées et vérifiées par empreinte ; roues binaires seulement : aucun script d'installation exécuté.
COPY requirements-simulator.txt .
RUN pip install --no-cache-dir --require-hashes --only-binary :all: -r requirements-simulator.txt
COPY restovision_edge restovision_edge
COPY config config
# Le simulateur n'a besoin d'aucun droit : il tourne sous un utilisateur sans privilèges.
RUN useradd --system --uid 10001 --no-create-home restovision
USER restovision
ENV PYTHONUNBUFFERED=1
ENTRYPOINT ["python", "-m", "restovision_edge", "--config", "config/edge.example.json", "--source", "simulate", "--publisher", "mqtt"]
