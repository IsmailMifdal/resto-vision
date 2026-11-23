FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# Versions du package-lock.json, sans exécuter les scripts d'installation des paquets.
RUN npm ci --ignore-scripts
COPY . .
# Le compilateur Angular installé ci-dessus (version figée), pas un téléchargement à la volée.
RUN npm run build

# nginx sans droits root : il écoute sur le port 8080 au lieu de 80.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/web/browser /usr/share/nginx/html
EXPOSE 8080
