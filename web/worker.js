// ChessCrab motor/analiz işçisi: Rust (WASM) motoru arayüzü dondurmadan burada çalışır.
import init, { CrabEngine, CrabAnalyzer } from "./pkg/chesscrab_wasm.js";

const ready = init();
let engine = null;

self.onmessage = async (e) => {
  await ready;
  const { id, op, args } = e.data;
  try {
    let result = null;
    if (op === "best_move") {
      engine ??= new CrabEngine(32);
      const r = engine.best_move(args.start_fen ?? undefined, JSON.stringify(args.moves), args.level, args.movetime ?? undefined);
      result = r ? JSON.parse(r) : null;
    } else if (op === "new_game") {
      engine?.new_game();
    } else if (op === "evaluate") {
      engine ??= new CrabEngine(32);
      result = JSON.parse(engine.evaluate(args.fen, args.depth, args.multipv ?? 3, args.movetime ?? undefined));
    } else if (op === "analyze_range") {
      // [start, end) aralığını sondan başa değerlendir (TT sonraki pozisyonlardan faydalanır)
      const a = new CrabAnalyzer(args.pgn, args.depth);
      const out = new Array(args.end - args.start);
      for (let i = args.end - 1; i >= args.start; i--) {
        out[i - args.start] = JSON.parse(a.eval(i));
        self.postMessage({ id, progress: 1 });
      }
      a.free();
      result = out;
    } else {
      throw new Error("bilinmeyen işlem: " + op);
    }
    self.postMessage({ id, result });
  } catch (err) {
    self.postMessage({ id, error: String(err?.message ?? err) });
  }
};
