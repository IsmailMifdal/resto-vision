// Service worker minimal : sur téléphone, les notifications d'alerte passent par lui (la montre les reprend).
// Il ne met rien en cache : l'application doit toujours afficher l'état réel de la salle.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

// Toucher la notification ramène sur l'écran Salle.
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const open = windows.find((client) => 'focus' in client);
      return open ? open.focus() : self.clients.openWindow('/salle');
    }),
  );
});
