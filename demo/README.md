# Demo portfolio

This directory contains a small synthetic Portfolio Performance file for local
testing and demos.

- `demo.portfolio` is plain XML in the Portfolio Performance file format.
- The data is fictional and contains no broker credentials or real account
  details.
- The file is intentionally not stored under `portfolios/` because that
  directory is used for local runtime data and is ignored by git.

## Use with the server API

Point `PORTFOLIO_DIR` at this directory when running the headless server:

```bash
export PORTFOLIO_DIR=/path/to/portfolio-performance/demo
./run-server.sh
```

The portfolio API lists `.portfolio` and `.xml` files from `PORTFOLIO_DIR`.
Portfolio IDs are derived from each file path relative to `PORTFOLIO_DIR`, so
keeping the file name as `demo.portfolio` gives a stable ID for local demos.

If you use a runtime-only `portfolios/` directory instead, copy or symlink this
file there:

```bash
mkdir -p portfolios
ln -sf ../demo/demo.portfolio portfolios/demo.portfolio
```
