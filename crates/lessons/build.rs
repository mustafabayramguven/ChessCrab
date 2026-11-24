use std::fs;
use std::io::Write;
use std::path::Path;

fn walk(dir: &Path, base: &Path, out: &mut Vec<String>) {
    let Ok(rd) = fs::read_dir(dir) else { return };
    let mut entries: Vec<_> = rd.flatten().collect();
    entries.sort_by_key(|e| e.path());
    for e in entries {
        let p = e.path();
        if p.is_dir() {
            walk(&p, base, out);
        } else if p.extension().and_then(|x| x.to_str()) == Some("toml") {
            out.push(p.strip_prefix(base).unwrap().to_string_lossy().replace('\\', "/"));
        }
    }
}

fn main() {
    let manifest = std::env::var("CARGO_MANIFEST_DIR").unwrap();
    let content = Path::new(&manifest).join("content");
    println!("cargo:rerun-if-changed=content");
    let mut files = Vec::new();
    walk(&content, &content, &mut files);
    let out_dir = std::env::var("OUT_DIR").unwrap();
    let mut f = fs::File::create(Path::new(&out_dir).join("content_files.rs")).unwrap();
    writeln!(f, "pub static FILES: &[(&str, &str)] = &[").unwrap();
    for rel in &files {
        println!("cargo:rerun-if-changed=content/{rel}");
        writeln!(f, "    ({rel:?}, include_str!(concat!(env!(\"CARGO_MANIFEST_DIR\"), \"/content/{rel}\"))),").unwrap();
    }
    writeln!(f, "];").unwrap();
}
