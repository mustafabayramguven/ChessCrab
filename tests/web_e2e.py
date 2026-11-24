"""Web uygulamasının uçtan uca testi: motor, analiz ve dersler tarayıcıda (WASM),
sunucu sadece giriş/senkronizasyon. Kullanım: python tests/web_e2e.py http://127.0.0.1:3437"""
import asyncio, json, os, sys
from playwright.async_api import async_playwright

B = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:3437"
OUT = os.environ.get("SHOTS", "shots")
OPERA = """[White "Paul Morphy"]
[Black "Duke Karl / Count Isouard"]
[Result "1-0"]

1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7 8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7 14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8# 1-0"""

def check(cond, msg):
    print(("OK   " if cond else "HATA ") + msg)
    if not cond:
        check.failed = True
check.failed = False

async def main():
    os.makedirs(OUT, exist_ok=True)
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(viewport={"width": 1280, "height": 900})
        errs, api_calls = [], []
        pg.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
        pg.on("pageerror", lambda e: errs.append(str(e)))
        pg.on("request", lambda r: api_calls.append(r.url.split(B)[-1]) if "/api/" in r.url else None)
        await pg.goto(B)
        await pg.wait_for_function("document.querySelector('#play-level').options.length > 0", timeout=30000)
        check("WASM" in await pg.inner_text("#ver"), "WASM çekirdeği yüklendi: " + await pg.inner_text("#ver"))

        # Oyna (giriş yapmadan): motor tarayıcıda
        await pg.select_option("#play-level", "2")
        await pg.click("#play-new"); await pg.wait_for_timeout(500)
        await pg.click('#play-board .sq[data-sq="e2"]'); await pg.click('#play-board .sq[data-sq="e4"]')
        await pg.wait_for_function("document.querySelectorAll('#play-moves .m').length >= 2", timeout=20000)
        check(True, "CrabFish tarayıcıda hamle yaptı: " + (await pg.inner_text("#play-moves")).replace("\n", " "))
        await pg.screenshot(path=f"{OUT}/oyna.png")

        # Analiz: Web Worker havuzu
        await pg.click("nav button[data-v=analysis]")
        await pg.fill("#an-pgn", OPERA)
        await pg.select_option("#an-depth", "10")
        await pg.click("#an-run")
        await pg.wait_for_selector("#an-result", state="visible", timeout=180000)
        info = await pg.inner_text("#an-info"); acc = await pg.inner_text("#an-acc")
        check("çekirdek" in info, "analiz cihazda: " + info)
        check("%" in acc, "doğruluk: " + acc.replace("\n", " ")[:120])
        await pg.click('#an-moves .m[data-ply="31"]')
        await pg.screenshot(path=f"{OUT}/analiz.png")

        # Dersler (yerel)
        await pg.click("nav button[data-v=lessons]"); await pg.wait_for_timeout(300)
        n_lessons = await pg.locator(".lesson").count()
        check(n_lessons >= 50, f"{n_lessons} ders listelendi")
        await pg.click('.lesson[data-id="taktik-catal"]'); await pg.click("#ls-next")
        await pg.click('#ls-board .sq[data-sq="e4"]'); await pg.click('#ls-board .sq[data-sq="f6"]')
        check("Doğru" in await pg.inner_text("#ls-feedback"), "ders hamlesi doğrulandı")
        await pg.screenshot(path=f"{OUT}/ders.png")

        # Repertuvar (yerel SRS)
        await pg.click("nav button[data-v=repertoire]"); await pg.wait_for_timeout(300)
        await pg.click('button[data-rep="siyah-caro"]'); await pg.wait_for_timeout(300)
        await pg.click('#dr-board .sq[data-sq="c7"]'); await pg.click('#dr-board .sq[data-sq="c6"]')
        check("Doğru" in await pg.inner_text("#dr-feedback"), "repertuvar hamlesi doğrulandı")

        engine_calls = [c for c in api_calls if not c.startswith(("/api/sync", "/api/auth"))]
        check(not engine_calls, f"sunucuya satranç isteği gitmedi ({engine_calls})")

        # Hesap + senkronizasyon
        await pg.click("nav button[data-v=account]")
        await pg.fill("#acc-user", "mustafa"); await pg.fill("#acc-pass", "gizli123")
        await pg.click("#acc-register"); await pg.wait_for_timeout(1000)
        check("mustafa" in await pg.inner_text("#who"), "kayıt/giriş")
        await pg.click("nav button[data-v=play]")
        await pg.click("#play-resign"); await pg.wait_for_timeout(1500)
        recs = await pg.evaluate("""async () => (await fetch('/api/records/games', {headers: {authorization: 'Bearer ' + JSON.parse(localStorage.getItem('cc.token'))}})).json()""")
        check(len(recs) == 1 and "pgn" in recs[0]["data"], "oyun sunucuya senkronize edildi")

        check(not errs, f"konsol hatası yok {errs}")
        await b.close()
    sys.exit(1 if check.failed else 0)

asyncio.run(main())
