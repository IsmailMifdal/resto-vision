/** Adresses du backend : localhost en développement, Railway en production. */
const PROD_BACKEND = 'backend-production-589082fd.up.railway.app';

const isLocal =
  typeof window === 'undefined' || ['localhost', '127.0.0.1'].includes(window.location.hostname);

export const API_URL = isLocal ? 'http://localhost:8080/api' : `https://${PROD_BACKEND}/api`;
export const WS_URL = isLocal ? 'ws://localhost:8080/ws' : `wss://${PROD_BACKEND}/ws`;
