# About

[Portfolio Performance](https://www.portfolio-performance.info): Track and evaluate the performance of your investment portfolio across stocks, cryptocurrencies, and other assets.

## This checkout (investments workspace)

If this repository sits beside **`tws_api`**, **`ops`**, **`pp-web`**, etc., treat it as part of a larger workspace:

- **Fork lineage:** this repo is a **fork** of upstream **[Portfolio Performance](https://github.com/portfolio-performance/portfolio)** with local changes (e.g. **REST/API server mode**, Redis integration, Flex workflows). **`pp-api`** and **`pp-ui`** Compose services use the **same Java codebase** built with different **`RUN_MODE`** / entrypoints — API-oriented server vs **RCP desktop UI** in VNC (see sibling **ops** **`compose.yml`**).
- **Role here:** the **Java portfolio manager** — Portfolio Performance holds the **domain model** and runs **logic and computational actions** against stored portfolio data (performance, valuations, imports, reports). **`pp-web`** is the Next.js UI; **`pp-api`** (server mode from this tree) exposes portfolio HTTP APIs; **`pp-ui`** runs the classic PP UI for operators who need the full desktop app in the browser via **websockify** (**noVNC**).
- **Code layout (Tycho):** Eclipse/OSGi modules live under paths such as **`portfolio-app/`** (build orchestration), **`name.abuchen.portfolio/`** (core domain, imports, calculations), **`name.abuchen.portfolio.ui/`** (RCP UI when built as desktop). Server/container images bundle selected bundles — follow existing **`RUN_MODE`** / launcher wiring in your Dockerfile or ops compose (**`RUN_MODE=server`** vs **`RUN_MODE=ui`** with VNC).
- **Flex / imports:** Flex-related directories and Redis-assisted import toggles are configured via compose env (prefixes like **`FLEX_*`**, **`REDIS_*`**) — see **ops** **`compose.yml`** for this checkout.
- **Build / test commands** for Maven Tycho and Eclipse live in **[CONTRIBUTING.md](CONTRIBUTING.md)** and **[CLAUDE.md](CLAUDE.md)** — prefer those files for exact `mvn` invocations (avoid duplicating long command lines elsewhere).
- **Runtime wiring** (Compose service `pp-api`, Redis, Flex paths) is defined in the **ops** repo’s compose files; see sibling **[ops/README.md](../ops/README.md)** and **[ops/docs/RUNBOOK.md](../ops/docs/RUNBOOK.md)**.
- **Agents / Cursor:** the workspace root **[AGENTS.md](../AGENTS.md)** summarizes cross-repo entry points; use **CLAUDE.md** here for Java-specific workflows.

Upstream-facing README content below is unchanged for Portfolio Performance contributors.

## Status

[![Build Status](https://github.com/portfolio-performance/portfolio/workflows/CI/badge.svg)](https://github.com/portfolio-performance/portfolio/actions?query=workflow%3ACI) [![Latest Release](https://img.shields.io/github/release/buchen/portfolio.svg)](https://github.com/portfolio-performance/portfolio/releases/latest) [![Release Date](https://img.shields.io/github/release-date/buchen/portfolio?color=blue)](https://github.com/portfolio-performance/portfolio/releases/latest) [![License](https://img.shields.io/github/license/buchen/portfolio.svg)](https://github.com/portfolio-performance/portfolio/blob/master/LICENSE)

[![LOC](https://sonarcloud.io/api/project_badges/measure?project=name.abuchen.portfolio%3Aportfolio-app&metric=ncloc)](https://sonarcloud.io/dashboard?id=name.abuchen.portfolio%3Aportfolio-app) [![Bugs](https://sonarcloud.io/api/project_badges/measure?project=name.abuchen.portfolio%3Aportfolio-app&metric=bugs)](https://sonarcloud.io/project/issues?id=name.abuchen.portfolio%3Aportfolio-app&resolved=false&types=BUG) [![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=name.abuchen.portfolio%3Aportfolio-app&metric=vulnerabilities)](https://sonarcloud.io/project/issues?id=name.abuchen.portfolio%3Aportfolio-app&resolved=false&types=VULNERABILITY) [![Code Coverage](https://sonarcloud.io/api/project_badges/measure?project=name.abuchen.portfolio%3Aportfolio-app&metric=coverage)](https://sonarcloud.io/component_measures?id=name.abuchen.portfolio%3Aportfolio-app&metric=Coverage)


## Links

* [Homepage](https://www.portfolio-performance.info)
* [Downloads](https://github.com/portfolio-performance/portfolio/releases)
* [Forum](https://forum.portfolio-performance.info/)
* [Manual](https://help.portfolio-performance.info/en)


## Contributing Source Code

* [Development setup](CONTRIBUTING.md#development-setup)
* [Project setup](CONTRIBUTING.md#project-setup)
* [Contribute code](CONTRIBUTING.md#contribute-code)
* [Images, Logo & Colors](CONTRIBUTING.md#images-logo-and-color)
* [Translations](CONTRIBUTING.md#translations)
* [Interactive-Flex-Query importer](CONTRIBUTING.md#interactive-flex-query-importer)
* [PDF importer](CONTRIBUTING.md#pdf-importer)
* [Trade calendar](CONTRIBUTING.md#trade-calendar)


## License

Eclipse Public License
https://www.eclipse.org/legal/epl-v10.html
