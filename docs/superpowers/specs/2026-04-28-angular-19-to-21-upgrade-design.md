# Angular 19 → 21 Upgrade — Design

**Date:** 2026-04-28
**Scope:** `ui-ngx/` Angular frontend of TBMQ CE

> **Model note:** Use Opus for brainstorming, writing the spec, and writing the implementation plan. **Switch to Sonnet (high reasoning) before executing the plan** — the upgrade work is mechanical (`ng update`, version bumps, fix-the-build loop) and Sonnet is faster and cheaper for it. Flip back to Opus only if a genuinely ambiguous decision appears (e.g., the custom esbuild builder has no v21 release and we need to reason about migrating to stock `@angular/build`).
**Driver:** Angular 19 reaches end of LTS support; need to land on a supported major (21).
**Out of scope:** Adopting v20/v21 idioms (signals, new control flow, zoneless, standalone migration, signal forms, mat-chip new APIs). Those are captured as a follow-up backlog.

---

## Goals

- Land the app on Angular 21 with a clean production build, passing lint, and no UI regressions across all top-level pages.
- Keep code shape unchanged: NgModule-based, `*ngIf`/`*ngFor`, reactive forms, NgRx slices, custom esbuild builder.
- Bring Angular peer-dependency libraries (NgRx, Material, CDK, angular-eslint, custom-esbuild builder, etc.) along to compatible majors.
- Preserve the Maven-driven build pipeline (`frontend-maven-plugin` → `yarn build:prod` → artifacts in `target/generated-resources/public/`).

## Non-goals

- No app-wide refactors to v20/v21 APIs.
- No Tailwind v4, no React/Chart.js/mqtt/marked upgrades, no NgRx signal-store migration.
- No new tests added; verification is build + lint + manual click-through.

---

## Baseline (current state, snapshot 2026-04-28)

- Angular core: `19.0.6` (animations, common, compiler, core, forms, platform-browser, router)
- Angular Material / CDK: `19.0.5`
- Angular CLI / devkit: `19.0.7`
- Angular build: `19.0.7` (used only for `extract-i18n` and `lint` builder)
- Builder in active use: `@angular-builders/custom-esbuild:application` and `:dev-server` (v18.0.0)
- TypeScript: `~5.5.4`
- ESLint: `9.17`, `typescript-eslint` `8.18`, `angular-eslint` `18.4.3`
- NgRx: `18.1.1` (store, effects, store-devtools)
- Node pin in `ui-ngx/pom.xml` (`frontend-maven-plugin`): `v20.18.0`
- `@types/node`: `^18.15.11`
- `zone.js`: `~0.15.0`
- Tailwind: `3.4.15` (out of scope)
- `patch-package` is wired into `prepare` script but `ui-ngx/patches/` does not exist (no-op).
- Notable third-party Angular libs still on v18 peer ranges: `@flowjs/ngx-flow` 18.0.1, `ngx-markdown` ^18.1.0, `ngx-drag-drop` ^18.0.2, `ngx-hm-carousel` ^18.0.0, `@iplab/ngx-color-picker` ^18.0.1, `ngx-clipboard` ^16.0.0.

## Decisions

| # | Question | Decision |
|---|---|---|
| Q1 | Driver | Stay supported. mat-chip new API deferred to a later effort. |
| Q2 | Node / CI | Free to bump local Node and `frontend-maven-plugin` Node pin. |
| Q3 | Lagging deps | Hybrid: prefer version bump; swap to alternative when a lib has no v20/v21 release and replacement is cheap. |
| Q4 | `patch-package` patches | N/A — no patches exist. May clean the `prepare` script later. |
| Q5 | Custom esbuild builder | Keep `@angular-builders/custom-esbuild`; bump to v20/v21 releases. |
| Q6 | Verification | Build + lint + manual click-through every top-level page. No new automated tests. |
| Q7 | mat-chip features | Out of scope; deferred. |
| Q8 | Branching / PR | Single PR, commit-per-phase for review tractability. |
| Q9 | Rollback | Decide in the moment if a blocker appears. |

## Approach

Use Angular's official migration tooling (`ng update`) as the spine. Per phase, run `ng update` for `@angular/core`, `@angular/cli`, then `@angular/material`/`cdk`, let schematics rewrite mechanical changes, then bump co-traveling deps to compatible majors and resolve any remaining compile/lint breakage.

Two phases sequenced in a single PR:

1. **Phase A: 19 → 20**
2. **Phase B: 20 → 21**

Phase A must fully pass the verification gate before Phase B begins.

---

## Phase A — 19 → 20

Each numbered step is intended to be a separate commit on the upgrade branch.

1. **Bump Node.**
   - Local: install Node ≥ 20.19 LTS (or 22 LTS).
   - Maven: change `<nodeVersion>` in `ui-ngx/pom.xml` from `v20.18.0` to a v20.19.x or v22.x LTS that satisfies Angular 20's engine requirement.
   - Bump `@types/node` to `^20.x` to match runtime.
2. **`ng update` Angular core + CLI to v20.** Accept schematic migrations as-is; do not opt into standalone or new-control-flow migrations.
3. **`ng update` Angular Material + CDK to v20.** Apply schematic migrations.
4. **Bump Angular toolchain peers** to v20-compatible versions:
   - `@angular-devkit/core`, `@angular-devkit/schematics`
   - `@angular/build`
   - `@angular/compiler-cli`, `@angular/language-service`
   - `@angular-builders/custom-esbuild` (v20 release)
   - `typescript` (per Angular 20 peer range)
   - `zone.js` (per Angular 20 peer range)
5. **Bump ESLint stack** to angular-eslint v20 (and any compatible `eslint` / `typescript-eslint` minor bumps it requires).
6. **Bump NgRx** (`@ngrx/store`, `@ngrx/effects`, `@ngrx/store-devtools`) to v20.x.
7. **Bump / decide on third-party Angular libs** (decide in the moment per the rule below):
   - `ngx-markdown`, `@flowjs/ngx-flow`, `ngx-drag-drop`, `ngx-hm-carousel`, `@iplab/ngx-color-picker`, `@mat-datetimepicker/core`
   - For each: bump to a v20-compatible release if one exists; if none, try peer overrides; if that fails, propose a swap or escalate.
   - **`ngx-clipboard`**: replace with `@angular/cdk/clipboard` regardless of release availability — cheap swap, removes a stale dep.
8. **Resolve remaining TypeScript / template / lint errors** that schematics didn't catch. Keep changes minimal.
9. **Phase A verification gate** (see below). Phase A is not done until all checks pass.

### Decide-in-the-moment rule for `Decide` libs

If a third-party Angular library has no compatible release for the target major:

1. Try installing with peer override (`--legacy-peer-deps` or yarn `resolutions`) and verify the lib still works at runtime in the manual smoke pass.
2. If runtime fails, evaluate: is usage trivial enough to drop? Is there an obvious replacement (CDK equivalent, native browser API, small inline component)?
3. If neither (1) nor (2) is reasonable, escalate to the human for a call: hold the phase, swap aggressively, or roll back to the prior major.

---

## Phase B — 20 → 21

Same shape as Phase A, retargeted to v21:

1. **Re-check Node floor** for Angular 21 (likely still ≥ 20.19 / 22.12); bump Maven and local if required.
2. **`ng update` core + CLI to v21.**
3. **`ng update` Material + CDK to v21.**
4. **Bump Angular toolchain peers** (`@angular/build`, devkit, custom-esbuild, compiler-cli, language-service, typescript, zone.js).
5. **Bump angular-eslint to v21** + dependent eslint/typescript-eslint minors.
6. **Bump NgRx to v21.x.**
7. **Bump / decide on third-party libs** (same list as Phase A, retargeted).
8. **Resolve remaining errors.**
9. **Phase B verification gate.**

## Final commit

- Run full Maven UI build: `mvn clean install -pl ui-ngx -am`. Confirm artifacts land in `ui-ngx/target/generated-resources/public/` and the backend can serve them.
- Update `.claude/CLAUDE.md` only if framework version statements changed (currently it says "Angular 19" — update to "Angular 21").
- PR description summarizes both phases, lists any libs swapped or pinned with peer overrides, and includes the click-through verification list with statuses.

---

## Verification gate (run after each phase)

1. **Clean install.** Delete `node_modules`. Keep `yarn.lock` (so the diff is reviewable). Run `yarn install`. Audit and resolve peer warnings.
2. **Lint.** `yarn lint` passes clean. Auto-fix mechanical issues; review the rest.
3. **Production build.** `yarn build:prod` succeeds. Output lands in `target/generated-resources/public/`.
4. **Maven build.** `mvn clean install -pl ui-ngx -am` succeeds — confirms the Node bump works in the Maven flow.
5. **Dev server smoke.** `yarn start`, log in against a running backend at `localhost:8083`, click through:
   - Client Sessions (list, detail, disconnect)
   - Subscriptions
   - Retained Messages
   - MQTT Client Credentials (list, create, edit)
   - Auth Providers
   - Integrations (list, create, view logs)
   - WebSocket Client tool (connect, publish, subscribe)
   - Kafka management pages
   - Dashboard / home charts
   - Settings, Profile, Logout
6. **Console check.** No new errors or warnings versus a baseline captured before Phase A. Capture the baseline before any code changes.
7. **Bundle size sanity.** Run `yarn bundle-report` and compare totals to baseline. Flag any regression greater than ~10% for human review.

A phase is "done" only when all 7 pass.

---

## Risks

- **Third-party lib lag.** `@flowjs/ngx-flow`, `ngx-hm-carousel`, `@mat-datetimepicker/core`, `@iplab/ngx-color-picker` historically release slowly. Mitigation: decide-in-the-moment rule; replacement options surfaced in the backlog.
- **Custom esbuild builder lag.** `@angular-builders/custom-esbuild` releases follow Angular but may trail by days/weeks. Mitigation: pin to highest available; if it blocks v21, evaluate temporary fall-back to stock `@angular/build:application` and reverse engineer the custom config (only if blocking).
- **NgRx 18 → 20 / 21 migrations.** NgRx 19 introduced API tweaks; `ng update` schematics handle most. Mitigation: run schematics, review diff, smoke-test the four state slices (auth, load, settings, notification).
- **TypeScript major bump.** Angular 20 expects TS 5.8+. Strictness or new diagnostics may surface latent issues. Mitigation: fix as encountered; do not silence with `// @ts-ignore`.
- **Maven Node pin drift.** A stale Maven Node pin will pass locally but fail in CI. Mitigation: gate verification step 4 (Maven build) is mandatory.
- **Long-lived branch divergence.** Single PR means the branch lives until both phases pass. Mitigation: rebase frequently; keep phases small and committed.

---

## v21 nice-to-have backlog (deferred — not part of this upgrade)

These are explicitly out of scope for this upgrade and tracked here so they're not lost. Each gets its own brainstorm → spec → plan when prioritized.

1. **mat-chip with built-in remove / edit buttons** (`mat-chip-grid` + `mat-chip-row` + `MatChipEditInput`) — the original driver behind the upgrade interest.
2. **New control flow** (`@if` / `@for` / `@switch`) — schematic-driven migration from structural directives.
3. **Standalone components migration** — schematic-driven removal of most `NgModule` boilerplate.
4. **Signal inputs / outputs / queries** (`input()`, `output()`, `viewChild()`) — adopt incrementally.
5. **Signal-based forms** (v21) — evaluate stability before adopting.
6. **Zoneless change detection** — biggest perf win, biggest risk; defer until signals are widely adopted.
7. **`afterRenderEffect` / `afterNextRender`** — replace ad-hoc render hooks where present.
8. **Deferrable views (`@defer`)** — apply to heavy panels (charts, ace editor, integrations).
9. **`provideHttpClient(withFetch())`** — modern HTTP backend.
10. **NgRx signal store** — evaluate replacing existing slices with `@ngrx/signals`.
11. **Drop `@angular-builders/custom-esbuild`** in favor of stock `@angular/build:application` if the custom config is no longer needed.
12. **Tailwind v4** — separate effort; v4's CSS-first config is a real migration.
13. **Drop `patch-package`** from the `prepare` script (no patches exist).

---

## Acceptance criteria

- All Angular packages on `21.x.x`.
- All Angular Material / CDK packages on `21.x.x`.
- `yarn lint`, `yarn build:prod`, and `mvn clean install -pl ui-ngx -am` all pass on the upgrade branch.
- Manual click-through of every top-level page passes with no new console errors and no visible regressions versus baseline.
- PR description documents any libraries swapped, pinned via peer override, or otherwise deviated from a clean version bump.
