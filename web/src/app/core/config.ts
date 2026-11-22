/** Adresses du backend.
 *  - Développement (ng serve, port 4200) : le backend tourne à part sur le port 8080.
 *  - Hébergement Railway : l'application et le backend sont deux services séparés.
 *  - Sinon (kit Docker) : l'application est servie par nginx, qui relaie /api et /ws vers le backend. */
const RAILWAY_BACKEND = 'backend-production-589082fd.up.railway.app';

const dev = location.port === '4200';
// Frontend hébergé à part (Railway ou Vercel) : le backend est sur Railway.
const railway = ['.up.railway.app', '.vercel.app'].some((suffix) => location.hostname.endsWith(suffix));
const socket = location.protocol === 'https:' ? 'wss:' : 'ws:';

export const API_URL = dev ? 'http://localhost:8080/api' : railway ? `https://${RAILWAY_BACKEND}/api` : '/api';
export const WS_URL = dev
  ? 'ws://localhost:8080/ws'
  : railway ? `wss://${RAILWAY_BACKEND}/ws` : `${socket}//${location.host}/ws`;
