# Angular 19 → 21 Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Model note:** Use **Sonnet (high reasoning)** to execute this plan. Flip back to Opus only if a genuinely ambiguous decision appears (e.g., the custom esbuild builder has no v21 release and we need to reason about migrating to stock `@angular/build`).

**Goal:** Land `ui-ngx/` on Angular 21 with a clean production build, passing lint, no UI regressions, and no API rewrites — keep modules, structural directives, reactive forms, NgRx slices, and the custom esbuild builder.

**Architecture:** Two sequential phases (19→20, then 20→21) on a single branch with one PR. Each phase: bump Node, run `ng update` for core/CLI/Material, bump co-traveling deps (NgRx, angular-eslint, custom-esbuild builder, third-party Angular libs), resolve breakage, then pass a 7-step verification gate before starting the next phase.

**Tech Stack:** Angular 19→21, Angular Material, NgRx 18→21, TypeScript, ESLint 9 / typescript-eslint / angular-eslint, `@angular-builders/custom-esbuild`, `frontend-maven-plugin`, yarn classic.

**Spec:** `docs/superpowers/specs/2026-04-28-angular-19-to-21-upgrade-design.md`

**Decide-in-the-moment rule (used in steps below):** When a third-party Angular library has no compatible release for the target major:
1. Try peer override (`yarn install --ignore-peer-deps` / yarn `resolutions`); if runtime is fine in the smoke pass, accept it with a TODO.
2. If runtime fails, swap (CDK equivalent / drop / inline) when usage is small.
3. Otherwise escalate to the human.

---

## Pre-flight

### Task 0: Branch, baseline, and tools

**Files:**
- Modify (later): `ui-ngx/package.json`, `ui-ngx/yarn.lock`, `ui-ngx/pom.xml`
- Read: `ui-ngx/angular.json`, `ui-ngx/package.json`

- [ ] **Step 0.1: Create the upgrade branch from main**

```bash
cd /home/artem/projects/tbmq-ce
git checkout main
git pull --ff-only
git checkout -b chore/angular-19-to-21-upgrade
```

- [ ] **Step 0.2: Confirm local Node satisfies Angular 20's floor (≥ 20.19) and is an LTS**

Run: `node --version`
Expected: `v20.19.x` or `v22.x` LTS. If lower, install via `nvm install 22 && nvm use 22` (or 20.19+) before continuing.

- [ ] **Step 0.3: Capture baseline production build & bundle report**

```bash
cd ui-ngx
yarn install --frozen-lockfile
yarn lint > /tmp/baseline-lint.log 2>&1 || true
yarn build:prod > /tmp/baseline-build.log 2>&1
ls -lh target/generated-resources/public/ > /tmp/baseline-bundles.txt
yarn bundle-report > /tmp/baseline-bundle-report.log 2>&1 || true
```

Save these files outside the repo (e.g., `~/tbmq-upgrade-baseline/`) — they are the comparison reference for the verification gates.

- [ ] **Step 0.4: Capture baseline runtime console**

Start backend separately (`mvn spring-boot:run -pl application` in another terminal, or use an existing instance), then:

```bash
cd ui-ngx && yarn start
```

Open the dev server, log in, click through every page in the verification gate's smoke list (see "Verification gate" section), and capture any pre-existing console warnings/errors as the baseline. Stop the dev server when done.

- [ ] **Step 0.5: Commit baseline log files? — No.**

Baseline logs live outside the repo. Do not commit them. Only the working-tree state is versioned.

---

## Phase A — Angular 19 → 20

### Task A1: Bump Node pin in Maven

**Files:**
- Modify: `ui-ngx/pom.xml` (line ~58)

- [ ] **Step A1.1: Bump `<nodeVersion>` in `frontend-maven-plugin`**

Edit `ui-ngx/pom.xml` and change:

```xml
<nodeVersion>v20.18.0</nodeVersion>
```

to a current LTS that satisfies Angular 20's engine requirement (`≥ 20.19.0`). Pick the latest 22.x LTS to also clear Angular 21's likely floor:

```xml
<nodeVersion>v22.12.0</nodeVersion>
```

(Use whatever is the current 22.x LTS at the time of execution — check https://nodejs.org/en/about/previous-releases.)

- [ ] **Step A1.2: Bump `@types/node` in `package.json`**

Edit `ui-ngx/package.json` `devDependencies`:

```json
"@types/node": "^22.10.0",
```

(Match the major of the runtime Node chosen in A1.1. Patch version flexible.)

- [ ] **Step A1.3: Run `yarn install` to update the lockfile**

```bash
cd ui-ngx && yarn install
```

Expected: lockfile updates only for `@types/node` and its transitive deps. No build run yet.

- [ ] **Step A1.4: Sanity-check Maven Node bump**

```bash
cd /home/artem/projects/tbmq-ce
mvn -pl ui-ngx -am clean install -DskipTests -DskipUiTests=false -q
```

Expected: Maven downloads the new Node version and `yarn install` completes inside the Maven flow. Build still uses Angular 19, so this should succeed and produce a UI bundle. If it fails, fix before proceeding.

- [ ] **Step A1.5: Commit**

```bash
git add ui-ngx/pom.xml ui-ngx/package.json ui-ngx/yarn.lock
git commit -m "chore(ui): bump Node to 22 LTS for Angular 20 upgrade"
```

---

### Task A2: `ng update` Angular core + CLI to 20

**Files:**
- Modify (auto): `ui-ngx/package.json`, `ui-ngx/yarn.lock`, `ui-ngx/angular.json`, `ui-ngx/tsconfig*.json`, possibly `ui-ngx/src/**/*.ts`

- [ ] **Step A2.1: Run the core+CLI migration**

```bash
cd ui-ngx
yarn ng update @angular/core@20 @angular/cli@20 --force
```

Use `--force` because non-Angular peers (NgRx 18, ngx-* libs) will block without it; we'll bump them in later tasks.

Expected: schematics rewrite imports, possibly tsconfig flags, possibly `main.ts` / `app.module.ts` minor changes. Read the diff before continuing.

- [ ] **Step A2.2: Bump pinned `@angular/*` peers that `ng update` may not have touched**

Edit `ui-ngx/package.json` and ensure these are all on `^20.0.0` (or whatever `ng update` resolved for `@angular/core`):

```json
"@angular/animations": "^20.0.0",
"@angular/common": "^20.0.0",
"@angular/compiler": "^20.0.0",
"@angular/core": "^20.0.0",
"@angular/forms": "^20.0.0",
"@angular/platform-browser": "^20.0.0",
"@angular/platform-browser-dynamic": "^20.0.0",
"@angular/router": "^20.0.0",
"@angular/compiler-cli": "^20.0.0",
"@angular/language-service": "^20.0.0",
"@angular/build": "^20.0.0"
```

(Use the actual resolved version — check what `yarn list --pattern @angular/core` reports and align the others.)

```bash
cd ui-ngx && yarn install
```

- [ ] **Step A2.3: Try a build to surface the breakage surface**

```bash
yarn build:prod 2>&1 | tee /tmp/A2-build.log
```

Expected: build will likely fail because NgRx, custom-esbuild builder, angular-eslint, and third-party libs still resolve to v18-peer. Capture errors but do **not** fix them yet — they get fixed in Tasks A3–A6.

- [ ] **Step A2.4: Commit**

```bash
git add ui-ngx/
git commit -m "chore(ui): ng update core + cli to 20 (build breaks until peers bump)"
```

---

### Task A3: `ng update` Angular Material + CDK to 20

**Files:**
- Modify (auto): `ui-ngx/package.json`, `ui-ngx/yarn.lock`, `ui-ngx/src/**/*.{ts,html,scss}`

- [ ] **Step A3.1: Run the Material migration**

```bash
cd ui-ngx
yarn ng update @angular/material@20 @angular/cdk@20 --force
```

Expected: schematic edits to imports and a small number of templates/SCSS for any deprecated APIs. Read the diff carefully — Material schematics occasionally rewrite SCSS theme files.

- [ ] **Step A3.2: Spot-check theme & global styles**

```bash
git diff -- ui-ngx/src/theme.scss ui-ngx/src/styles.scss ui-ngx/src/scss/ 2>/dev/null
```

If theme changes look wrong (e.g., a custom mixin was rewritten), revert that file and re-apply changes manually.

- [ ] **Step A3.3: Commit**

```bash
git add ui-ngx/
git commit -m "chore(ui): ng update Material + CDK to 20"
```

---

### Task A4: Bump Angular toolchain + builder + TypeScript

**Files:**
- Modify: `ui-ngx/package.json`, `ui-ngx/yarn.lock`

- [ ] **Step A4.1: Bump devkit, custom-esbuild, TypeScript, zone.js**

Edit `ui-ngx/package.json` `devDependencies` (versions as of the time of execution — use latest stable that targets Angular 20):

```json
"@angular-builders/custom-esbuild": "^20.0.0",
"@angular-devkit/core": "^20.0.0",
"@angular-devkit/schematics": "^20.0.0",
"typescript": "~5.8.0",
```

And `dependencies`:

```json
"zone.js": "~0.15.1",
```

(Verify the Angular 20 peer range for `typescript` and `zone.js` from the `@angular/core` package's published `peerDependencies` — `npm view @angular/core@20 peerDependencies`.)

```bash
cd ui-ngx && yarn install
```

- [ ] **Step A4.2: Try a build**

```bash
yarn build:prod 2>&1 | tee /tmp/A4-build.log
```

Expected: fewer errors than A2.3, but still failing on NgRx and third-party libs.

- [ ] **Step A4.3: Commit**

```bash
git add ui-ngx/package.json ui-ngx/yarn.lock
git commit -m "chore(ui): bump Angular devkit, custom-esbuild builder, typescript, zone.js to 20-compatible"
```

---

### Task A5: Bump ESLint stack to angular-eslint 20

**Files:**
- Modify: `ui-ngx/package.json`, `ui-ngx/yarn.lock`, possibly `ui-ngx/eslint.config.js` or `.eslintrc.json`

- [ ] **Step A5.1: Bump angular-eslint and typescript-eslint to v20-compatible versions**

Check current latest:
```bash
npm view angular-eslint versions --json | tail -20
npm view typescript-eslint versions --json | tail -20
```

Edit `ui-ngx/package.json`:

```json
"angular-eslint": "^20.0.0",
"@angular-eslint/builder": "^20.0.0",
"typescript-eslint": "^8.30.0"
```

(`typescript-eslint` major may not need bumping; bump only if angular-eslint requires it.)

```bash
cd ui-ngx && yarn install
```

- [ ] **Step A5.2: Run lint to surface config drift**

```bash
yarn lint 2>&1 | tee /tmp/A5-lint.log
```

If the config file format changed (rare for minor angular-eslint majors), follow the migration note in the angular-eslint changelog.

- [ ] **Step A5.3: Auto-fix mechanical lint issues**

```bash
yarn lint --fix 2>&1 | tee /tmp/A5-lint-fix.log
```

Review the diff. Revert any auto-fix that looks semantically wrong.

- [ ] **Step A5.4: Commit**

```bash
git add ui-ngx/
git commit -m "chore(ui): bump angular-eslint to 20 and apply auto-fixes"
```

---

### Task A6: Bump NgRx to 20

**Files:**
- Modify: `ui-ngx/package.json`, `ui-ngx/yarn.lock`, possibly `ui-ngx/src/app/core/core.state.ts` and slice files

- [ ] **Step A6.1: Use NgRx's own migration schematic**

```bash
cd ui-ngx
yarn ng update @ngrx/store@20 @ngrx/effects@20 @ngrx/store-devtools@20 --force
```

NgRx ships migration schematics — they handle the API tweaks introduced between v18 and v20.

- [ ] **Step A6.2: Build to verify NgRx no longer blocks**

```bash
yarn build:prod 2>&1 | tee /tmp/A6-build.log
```

If errors remain, they should be from third-party non-NgRx Angular libs (handled in A7) or app code that needs adjustment for NgRx schematics.

- [ ] **Step A6.3: Smoke-test the four state slices**

Start dev server:
```bash
yarn start
```

Verify:
- `auth` slice — log in / log out work
- `load` slice — global loading bar appears during navigation
- `settings` slice — open Settings, change something, persists
- `notification` slice — open WebSocket Client, trigger a notification toast

- [ ] **Step A6.4: Commit**

```bash
git add ui-ngx/
git commit -m "chore(ui): ng update NgRx to 20 and verify slice behavior"
```

---

### Task A7: Bump (or decide on) third-party Angular libraries

**Files:**
- Modify: `ui-ngx/package.json`, `ui-ngx/yarn.lock`
- Possibly modify: source files that import a swapped library

The libs in scope (current versions on v18 peer):

| Library | Current | Action |
|---|---|---|
| `ngx-markdown` | ^18.1.0 | Bump to v20-compatible |
| `@flowjs/ngx-flow` | 18.0.1 | Bump if v20 release exists; else **decide** |
| `ngx-drag-drop` | ^18.0.2 | Bump if v20 release exists; else **decide** |
| `ngx-hm-carousel` | ^18.0.0 | Bump if v20 release exists; else **decide** |
| `@iplab/ngx-color-picker` | ^18.0.1 | Bump if v20 release exists; else **decide** |
| `@mat-datetimepicker/core` | ~14.0.0 | Bump to v20-compatible if exists; else **decide** |
| `ngx-clipboard` | ^16.0.0 | **Replace with `@angular/cdk/clipboard` regardless** |

- [ ] **Step A7.1: For each non-clipboard lib, check if a v20 release exists**

```bash
for pkg in ngx-markdown @flowjs/ngx-flow ngx-drag-drop ngx-hm-carousel @iplab/ngx-color-picker @mat-datetimepicker/core; do
  echo "=== $pkg ==="
  npm view "$pkg" versions --json 2>/dev/null | tail -10
done
```

For each: if a version exists whose Angular peer covers `^20.0.0`, target that. Otherwise apply the decide rule (peer override → swap → escalate).

- [ ] **Step A7.2: Replace `ngx-clipboard` with `@angular/cdk/clipboard`**

Find usages:
```bash
grep -rn "ngx-clipboard\|ClipboardModule\|ClipboardService" ui-ngx/src --include='*.ts' --include='*.html'
```

For each `.ts` import: replace `import { ClipboardModule, ClipboardService } from 'ngx-clipboard';` with `import { ClipboardModule, Clipboard } from '@angular/cdk/clipboard';` and rewrite call sites:

```ts
// Before:
this.clipboardService.copy(text);
// After:
this.clipboard.copy(text);
```

For templates using `[ngxClipboard]="..."` directive: switch to `[cdkCopyToClipboard]="..."`.

Then remove the dep:
```bash
yarn remove ngx-clipboard
```

- [ ] **Step A7.3: Bump remaining libs in `package.json`**

Edit `ui-ngx/package.json` with the targets resolved in A7.1. Example:

```json
"ngx-markdown": "^20.0.0",
"@flowjs/ngx-flow": "^20.0.0",
"ngx-drag-drop": "^20.0.0",
"ngx-hm-carousel": "^20.0.0",
"@iplab/ngx-color-picker": "^20.0.0"
```

Apply peer-override fallback only for libs that have no v20 release: keep them at the highest available version and document in the PR description.

```bash
cd ui-ngx && yarn install
```

- [ ] **Step A7.4: Build clean**

```bash
yarn build:prod 2>&1 | tee /tmp/A7-build.log
```

Expected: build succeeds. If errors remain, they are app-code adjustments needed for any swapped lib (e.g., clipboard call-site fixes missed).

- [ ] **Step A7.5: Commit**

```bash
git add ui-ngx/
git commit -m "chore(ui): bump third-party Angular libs to 20 and replace ngx-clipboard with CDK"
```

---

### Task A8: Resolve remaining errors

**Files:** any source files surfaced by the build / lint runs.

- [ ] **Step A8.1: Lint pass**

```bash
cd ui-ngx
yarn lint 2>&1 | tee /tmp/A8-lint.log
```

Fix issues by hand. Avoid silencing with `// eslint-disable` unless the rule is genuinely wrong for the codebase. Do not introduce `// @ts-ignore`.

- [ ] **Step A8.2: Build pass**

```bash
yarn build:prod 2>&1 | tee /tmp/A8-build.log
```

Fix any TypeScript / template / SCSS errors. Common Angular 20 surfaces:
- Stricter template type checking around `null`/`undefined`
- New Material API deprecations (warnings, not errors — note for backlog, don't rewrite)
- `inject()` recommended over constructor injection (warnings, not errors — ignore)

- [ ] **Step A8.3: Commit fixes**

```bash
git add ui-ngx/
git commit -m "chore(ui): resolve compile and lint issues after Angular 20 bump"
```

---

### Task A9: Phase A verification gate

Run the full 7-step gate from the spec. **Do not start Phase B until all 7 pass.**

- [ ] **Step A9.1: Clean install**

```bash
cd ui-ngx
rm -rf node_modules
yarn install
```

Expected: install completes; review and triage peer-dep warnings. Document any libs pinned with a peer-override warning.

- [ ] **Step A9.2: Lint clean**

```bash
yarn lint
```

Expected: exit code 0, no errors.

- [ ] **Step A9.3: Production build**

```bash
yarn build:prod
ls -lh target/generated-resources/public/
```

Expected: build succeeds; output present.

- [ ] **Step A9.4: Maven build**

```bash
cd /home/artem/projects/tbmq-ce
mvn -pl ui-ngx -am clean install -DskipTests
```

Expected: Maven build succeeds end-to-end with the new Node pin.

- [ ] **Step A9.5: Dev server smoke pass**

```bash
cd ui-ngx && yarn start
```

(Backend must be running at `localhost:8083`.) Click through and verify each page works without console errors:

- [ ] Login / Logout
- [ ] Client Sessions (list, detail open, disconnect)
- [ ] Subscriptions
- [ ] Retained Messages
- [ ] MQTT Client Credentials (list, create, edit)
- [ ] Auth Providers
- [ ] Integrations (list, create one, view logs)
- [ ] WebSocket Client tool (connect, publish, subscribe)
- [ ] Kafka management pages
- [ ] Dashboard / charts on home
- [ ] Settings page
- [ ] User Profile

- [ ] **Step A9.6: Console diff vs baseline**

Compare console errors/warnings in this pass against the baseline from Step 0.4. New errors → triage and fix in a follow-up commit on this phase. New warnings → note in PR description.

- [ ] **Step A9.7: Bundle size check**

```bash
yarn bundle-report 2>&1 | tee /tmp/A9-bundle.log
```

Compare bundle totals against `~/tbmq-upgrade-baseline/baseline-bundles.txt`. A regression > ~10% on the main bundle is worth investigating before continuing.

- [ ] **Step A9.8: Tag the phase commit**

```bash
git tag upgrade-phase-a-complete
```

Local tag, not pushed — used as a rollback anchor in case Phase B goes sideways.

---

## Phase B — Angular 20 → 21

Mirror of Phase A. Each task is the same shape, retargeted to v21.

### Task B1: Re-check Node floor for Angular 21

- [ ] **Step B1.1: Check Angular 21's `engines.node` requirement**

```bash
npm view @angular/core@21 engines
```

If higher than the v22 LTS already pinned, bump `ui-ngx/pom.xml` `<nodeVersion>` and `@types/node` accordingly. If satisfied, no change — skip B1.2 and the commit.

- [ ] **Step B1.2: If a bump was needed, install + commit**

```bash
cd ui-ngx && yarn install
git add ui-ngx/pom.xml ui-ngx/package.json ui-ngx/yarn.lock
git commit -m "chore(ui): bump Node pin for Angular 21 floor"
```

---

### Task B2: `ng update` Angular core + CLI to 21

- [ ] **Step B2.1: Run the core+CLI migration**

```bash
cd ui-ngx
yarn ng update @angular/core@21 @angular/cli@21 --force
```

- [ ] **Step B2.2: Align all `@angular/*` peers to ^21.0.0** (same list as A2.2, retargeted to 21)

```bash
yarn install
```

- [ ] **Step B2.3: Build, capture errors**

```bash
yarn build:prod 2>&1 | tee /tmp/B2-build.log
```

- [ ] **Step B2.4: Commit**

```bash
git add ui-ngx/
git commit -m "chore(ui): ng update core + cli to 21 (peers pending)"
```

---

### Task B3: `ng update` Angular Material + CDK to 21

- [ ] **Step B3.1: Run Material migration**

```bash
cd ui-ngx
yarn ng update @angular/material@21 @angular/cdk@21 --force
```

- [ ] **Step B3.2: Spot-check theme + global styles** (same as A3.2)

- [ ] **Step B3.3: Commit**

```bash
git add ui-ngx/
git commit -m "chore(ui): ng update Material + CDK to 21"
```

---

### Task B4: Bump toolchain + builder + TypeScript to 21-compatible

- [ ] **Step B4.1: Edit `ui-ngx/package.json`**

```json
"@angular-builders/custom-esbuild": "^21.0.0",
"@angular-devkit/core": "^21.0.0",
"@angular-devkit/schematics": "^21.0.0",
"typescript": "~5.9.0"
```

(Verify exact `typescript` peer with `npm view @angular/core@21 peerDependencies`.) Bump `zone.js` if Angular 21 still uses it (likely yes, possibly stricter version).

```bash
yarn install
```

- [ ] **Step B4.2: Build, capture errors**

```bash
yarn build:prod 2>&1 | tee /tmp/B4-build.log
```

- [ ] **Step B4.3: Commit**

```bash
git add ui-ngx/package.json ui-ngx/yarn.lock
git commit -m "chore(ui): bump devkit, custom-esbuild, typescript to 21-compatible"
```

**Escalation note:** If `@angular-builders/custom-esbuild` has no v21 release at the time of execution, stop and escalate. The fallback is migrating `angular.json` to stock `@angular/build:application` and `@angular/build:dev-server` — that is a real refactor, not a version bump, and warrants flipping back to Opus to scope it.

---

### Task B5: Bump angular-eslint to 21

- [ ] **Step B5.1: Bump and install**

```json
"angular-eslint": "^21.0.0",
"@angular-eslint/builder": "^21.0.0"
```

```bash
yarn install
yarn lint --fix 2>&1 | tee /tmp/B5-lint.log
```

- [ ] **Step B5.2: Commit**

```bash
git add ui-ngx/
git commit -m "chore(ui): bump angular-eslint to 21"
```

---

### Task B6: Bump NgRx to 21

- [ ] **Step B6.1: Run NgRx migration**

```bash
cd ui-ngx
yarn ng update @ngrx/store@21 @ngrx/effects@21 @ngrx/store-devtools@21 --force
```

- [ ] **Step B6.2: Build & smoke-test slices** (same as A6.2 / A6.3)

- [ ] **Step B6.3: Commit**

```bash
git add ui-ngx/
git commit -m "chore(ui): ng update NgRx to 21"
```

---

### Task B7: Bump (or decide on) third-party Angular libs to 21

Same matrix as Task A7, retargeted to v21. `ngx-clipboard` is already removed.

- [ ] **Step B7.1: Check v21 release availability**

```bash
for pkg in ngx-markdown @flowjs/ngx-flow ngx-drag-drop ngx-hm-carousel @iplab/ngx-color-picker @mat-datetimepicker/core; do
  echo "=== $pkg ==="
  npm view "$pkg" versions --json 2>/dev/null | tail -10
done
```

- [ ] **Step B7.2: Apply bumps in `package.json`** (target `^21.0.0` where available, peer-override fallback otherwise, escalate per the decide rule)

```bash
yarn install
```

- [ ] **Step B7.3: Build clean**

```bash
yarn build:prod 2>&1 | tee /tmp/B7-build.log
```

- [ ] **Step B7.4: Commit**

```bash
git add ui-ngx/
git commit -m "chore(ui): bump third-party Angular libs to 21"
```

---

### Task B8: Resolve remaining errors

(Same shape as Task A8 — lint + build, fix by hand, commit.)

- [ ] **Step B8.1: Lint pass**

```bash
cd ui-ngx && yarn lint 2>&1 | tee /tmp/B8-lint.log
```

- [ ] **Step B8.2: Build pass**

```bash
yarn build:prod 2>&1 | tee /tmp/B8-build.log
```

- [ ] **Step B8.3: Commit fixes**

```bash
git add ui-ngx/
git commit -m "chore(ui): resolve compile and lint issues after Angular 21 bump"
```

---

### Task B9: Phase B verification gate

Identical to Task A9 but at the v21 endpoint. Same 7 steps, same smoke-pass page list, same console-diff and bundle-size checks.

- [ ] B9.1 Clean install (`rm -rf node_modules && yarn install`)
- [ ] B9.2 `yarn lint`
- [ ] B9.3 `yarn build:prod` + check output
- [ ] B9.4 `mvn -pl ui-ngx -am clean install -DskipTests`
- [ ] B9.5 Dev server smoke pass through every page in the list (same checklist as A9.5)
- [ ] B9.6 Console-diff vs Step 0.4 baseline
- [ ] B9.7 Bundle-size diff vs Step 0.4 baseline
- [ ] B9.8 Tag the phase commit

```bash
git tag upgrade-phase-b-complete
```

---

## Final

### Task F1: Repo-level cleanup

**Files:**
- Modify: `.claude/CLAUDE.md`

- [ ] **Step F1.1: Update Angular version mention in `CLAUDE.md`**

In `/home/artem/projects/tbmq-ce/.claude/CLAUDE.md`, replace every `Angular 19` reference with `Angular 21`:

```bash
grep -n "Angular 19" .claude/CLAUDE.md
```

Edit each occurrence. (At baseline there are 2: in the "TBMQ" overview and in the "Frontend Architecture (Angular 19)" heading.)

- [ ] **Step F1.2: Commit**

```bash
git add .claude/CLAUDE.md
git commit -m "docs: update CLAUDE.md to reflect Angular 21"
```

### Task F2: Open the PR

- [ ] **Step F2.1: Push the branch**

```bash
git push -u origin chore/angular-19-to-21-upgrade
```

- [ ] **Step F2.2: Open PR with structured description**

```bash
gh pr create --title "chore(ui): upgrade Angular 19 → 21" --body "$(cat <<'EOF'
## Summary

Upgrades the `ui-ngx/` Angular frontend from 19 to 21 in two phases (19→20, 20→21). No API rewrites — modules, structural directives, reactive forms, and the custom esbuild builder are all kept. Replaces `ngx-clipboard` with `@angular/cdk/clipboard`. Bumps Node pin in `frontend-maven-plugin` to a 22 LTS.

Spec: `docs/superpowers/specs/2026-04-28-angular-19-to-21-upgrade-design.md`
Plan: `docs/superpowers/plans/2026-04-28-angular-19-to-21-upgrade.md`

## Notable changes

- Angular core / Material / CDK / CLI: 19 → 21
- NgRx: 18 → 21
- TypeScript, zone.js, angular-eslint, custom-esbuild builder bumped to 21-compatible
- `ngx-clipboard` removed; `@angular/cdk/clipboard` used instead
- (Document any libs pinned via peer-override and any libs swapped — fill in based on Phase A/B execution)

## Out of scope

- New control flow, signals, zoneless, standalone migration, mat-chip new APIs, Tailwind v4. Tracked in the spec's v21 nice-to-have backlog.

## Verification

- [x] `yarn lint` passes
- [x] `yarn build:prod` succeeds
- [x] `mvn -pl ui-ngx -am clean install -DskipTests` succeeds
- [x] Manual click-through pass on dev server (every top-level page)
- [x] No new console errors vs baseline
- [x] No bundle-size regression > 10%

## Rollback

Local tags `upgrade-phase-a-complete` and `upgrade-phase-b-complete` mark phase boundaries. To roll back to v20: `git reset --hard upgrade-phase-a-complete` (or revert the relevant commit range and re-push).
EOF
)"
```

---

## Self-review

**Spec coverage:**
- Phase A 19→20 (spec §"Phase A — 19 → 20") → Tasks A1–A9 ✓
- Phase B 20→21 (spec §"Phase B — 20 → 21") → Tasks B1–B9 ✓
- Final commit / Maven sanity / CLAUDE.md update (spec §"Final commit") → Task F1 ✓
- 7-step verification gate (spec §"Verification gate") → Tasks A9 / B9 ✓
- Decide-in-the-moment rule (spec §"Phase A · Decide rule") → restated at top of plan + applied in A7/B7 ✓
- v21 nice-to-have backlog (spec §"v21 nice-to-have backlog") → not implemented (intentional — backlog is in spec, not plan) ✓
- Acceptance criteria (spec §"Acceptance criteria") → mapped to Phase B verification gate (B9) and PR description (F2.2) ✓

**Placeholder scan:** none of the disallowed phrases used. Where exact patch versions cannot be predicted (e.g., "current 22.x LTS at time of execution"), the plan tells the engineer how to look up the actual value.

**Type/name consistency:** task IDs used consistently (A1.1, A9.7 etc.), tag names (`upgrade-phase-a-complete`, `upgrade-phase-b-complete`) referenced consistently between B9.8 and F2.2.

**Risks not covered by tasks:** none — every spec risk is touched by either the decide rule (lib lag), the Maven gate (Node pin drift), the verification gate (NgRx slice smoke), or escalation notes (custom-esbuild builder).
