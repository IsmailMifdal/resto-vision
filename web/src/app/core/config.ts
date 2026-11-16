/** Adresses du backend.
 *  En développement (ng serve, port 4200) le backend tourne à part sur le port 8080.
 *  Sinon l'application est servie par nginx, qui relaie /api et /ws vers le backend : même origine. */
const dev = location.port === '4200';
const socket = location.protocol === 'https:' ? 'wss:' : 'ws:';

export const API_URL = dev ? 'http://localhost:8080/api' : '/api';
export const WS_URL = dev ? 'ws://localhost:8080/ws' : `${socket}//${location.host}/ws`;
