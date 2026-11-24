// Çevrimdışı kullanım: uygulama dosyalarını önbellekte tut, API'yi her zaman ağdan iste.
const CACHE = "chesscrab-v1";
const FILES = ["./", "./index.html", "./worker.js", "./manifest.webmanifest", "./pkg/chesscrab_wasm.js", "./pkg/chesscrab_wasm_bg.wasm"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.pathname.startsWith("/api/")) return;
  // Ağ öncelikli (güncellemeler hemen gelsin), ağ yoksa önbellek
  e.respondWith(
    fetch(e.request)
      .then((r) => {
        if (r.ok && url.origin === location.origin) {
          const copy = r.clone();
          caches.open(CACHE).then((c) => c.put(e.request, copy));
        }
        return r;
      })
      .catch(() => caches.match(e.request).then((r) => r || caches.match("./index.html")))
  );
});
