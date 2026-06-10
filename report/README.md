# Course report (Typst)

The report source is `main.typ`; the submitted PDF is built from it.

## Dependency

The only dependency is **[Typst](https://typst.app/) ≥ 0.14** — a single binary, no
LaTeX needed. The template (`template/lib.typ`) uses no `@preview` packages, so
compilation works fully offline.

Install one of:

```bash
cargo install typst-cli          # via Rust
# or download a release binary: https://github.com/typst/typst/releases
# or: sudo snap install typst / brew install typst / pacman -S typst
```

## Build

```bash
typst compile main.typ           # one-shot -> main.pdf
typst watch main.typ             # recompiles on save, for editing
```

Red `«TODO: …»` markers in the PDF are placeholders that must be filled in before
submission (r-numbers, team number, regions, deployment URL + grader login, hours).
