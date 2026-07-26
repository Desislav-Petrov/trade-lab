# Plan #43: Containerized Release Pipeline — Task Decomposition

**Plan Issue:** https://github.com/Desislav-Petrov/trade-lab/issues/43  
**Goal:** Automated Docker image creation, GHCR publishing, and GitHub releases.

---

## Executive Summary

This decomposition covers 11 atomic INFRA setup tasks required to establish an automated release pipeline:

- Two GitHub Actions workflows (version creation + release automation)
- Backend Jib Docker plugin configuration
- Frontend Dockerfile with Vite build
- Docker Compose for local validation
- GHCR authentication and publishing
- GitHub release notes auto-generation

**Frontend tech:** Vite (pnpm)  
**Backend tech:** Spring Boot 4.1.0 (Gradle Kotlin DSL, Java 21)  
**Container registry:** GHCR at `ghcr.io/Desislav-Petrov/`  
**Trigger:** Manual version input → auto tag push → auto build/publish/release

---

## Tasks

### INFRA-1 — Create GitHub Actions: Create Version Workflow

**Task ID:** INFRA-1  
**Type:** INFRA setup  
**Plan:** Plan #43  
**Implements:** Workflow 1: Create Version (Manual)

**Description:**  
Create `.github/workflows/create-version.yml` — a manual workflow that accepts semantic version input, creates a git tag, and pushes it to trigger the release pipeline.

**Inputs:**
- Workflow trigger: manual dispatch with version input parameter (semantic format: `X.Y.Z`)
- Repository context: default branch, authenticated with GitHub token

**Outputs:**
- Git tag created: `vX.Y.Z`
- Tag pushed to origin
- Workflow logs documenting version creation

**Acceptance criteria:**
- [ ] Workflow file exists at `.github/workflows/create-version.yml`
- [ ] Workflow is triggered manually via GitHub Actions UI (workflow_dispatch)
- [ ] Accepts input parameter `version` with pattern validation (e.g., `^\d+\.\d+\.\d+$`)
- [ ] Creates annotated git tag with format `vX.Y.Z` (e.g., `v1.0.0`)
- [ ] Pushes tag to origin/main
- [ ] Workflow logs show version creation step and tag push confirmation
- [ ] Pushing the tag automatically triggers `release.yml` (verified by tag filter in release.yml: `on: push: tags: 'v*'`)

**Depends on:** none

---

### INFRA-2 — Create GitHub Actions: Release Pipeline Workflow

**Task ID:** INFRA-2  
**Type:** INFRA setup  
**Plan:** Plan #43  
**Implements:** Workflow 2: Release Pipeline (Automatic)

**Description:**  
Create `.github/workflows/release.yml` — an automatic workflow triggered by version tag push. Orchestrates backend image build, frontend image build, GHCR publishing, and GitHub release creation with auto-generated notes.

**Inputs:**
- Trigger: push event matching tag pattern `v*` (e.g., `v1.0.0`)
- Outputs from INFRA-1 (the version tag)
- GitHub token for authentication (auto-provided by Actions)

**Outputs:**
- Backend Docker image: `ghcr.io/Desislav-Petrov/trade-lab-backend:vX.Y.Z`
- Frontend Docker image: `ghcr.io/Desislav-Petrov/trade-lab-frontend:vX.Y.Z`
- GitHub Release named `vX.Y.Z` with auto-generated release notes

**Acceptance criteria:**
- [ ] Workflow file exists at `.github/workflows/release.yml`
- [ ] Triggered automatically on push matching `refs/tags/v*`
- [ ] Extracts version from tag (e.g., `v1.0.0` → version `1.0.0`)
- [ ] Has separate jobs for: backend build, frontend build, publish to GHCR, create release
- [ ] Backend build job runs INFRA-3 (Jib build)
- [ ] Frontend build job runs INFRA-6 (Docker build)
- [ ] Publish job requires successful completion of both build jobs
- [ ] Publish job uses GitHub token with `packages: write` permission
- [ ] Release job creates GitHub release with auto-generated notes comparing against previous release tag
- [ ] Workflow fails if any step fails (e.g., image push fails)

**Depends on:** INFRA-1, INFRA-3, INFRA-4, INFRA-6

---

### INFRA-3 — Backend: Configure Jib Gradle Plugin

**Task ID:** INFRA-3  
**Type:** INFRA setup  
**Plan:** Plan #43  
**Implements:** Backend Image Build — Plugin Configuration

**Description:**  
Add Jib Gradle plugin to `services/backend/build.gradle.kts`. Jib builds container images without a Dockerfile, directly from the Gradle build output.

**Inputs:**
- Current `build.gradle.kts` (Kotlin DSL)
- Jib plugin version (recommend latest stable: 3.4.x)
- Registry credentials (injected at runtime by GitHub Actions)

**Outputs:**
- Updated `build.gradle.kts` with Jib plugin declared
- Jib configuration ready for task definition (INFRA-4)

**Acceptance criteria:**
- [ ] Jib plugin added to `plugins {}` block in `build.gradle.kts`
- [ ] Plugin ID: `com.google.cloud.tools.jib`
- [ ] Plugin version 3.4.0 or later
- [ ] No inline configuration at plugin level (defer to task)
- [ ] Build still compiles and passes tests with plugin added
- [ ] No Dockerfile created yet (Jib will handle containerization)

**Depends on:** none

---

### INFRA-4 — Backend: Create Jib Image Build Task

**Task ID:** INFRA-4  
**Type:** INFRA setup  
**Plan:** Plan #43  
**Implements:** Backend Image Build — Gradle Task

**Description:**  
Add Jib task configuration in `services/backend/build.gradle.kts` to build and push Docker images to GHCR. Task will:
- Use Spring Boot-built JAR as image base
- Apply version tag from git tag (injected by workflow)
- Push to `ghcr.io/Desislav-Petrov/trade-lab-backend:vX.Y.Z`
- Support local builds and CI/CD builds

**Inputs:**
- Jib plugin (from INFRA-3)
- Version variable (from environment or gradle property, set by workflow)
- GHCR registry URL: `ghcr.io/Desislav-Petrov/`
- Docker credentials (GitHub token, injected by workflow as GHCR_TOKEN)

**Outputs:**
- Gradle task: `jibBuild` or similar (for local build testing)
- Gradle task: `jib` (standard Jib task that builds and pushes)
- Built image ready for push to GHCR

**Acceptance criteria:**
- [ ] Jib configuration block added to `build.gradle.kts`
- [ ] Image name configured: `ghcr.io/Desislav-Petrov/trade-lab-backend`
- [ ] Image tags configured: applies version from `project.version` or gradle property `imageVersion`
- [ ] Base image is suitable for Spring Boot (e.g., Eclipse Temurin JDK 21 or similar)
- [ ] Jib configured to use distroless or minimal base (e.g., `eclipse-temurin:21-jre`)
- [ ] Task supports setting version via gradle property: `./gradlew jib -PimageVersion=1.0.0`
- [ ] Image metadata includes: labels for version, commit, build timestamp (optional but recommended)
- [ ] GHCR registry URL and credentials configured (placeholders for token injection)
- [ ] Build without push works locally (dry-run / offline mode)
- [ ] Task `./gradlew jib` pushes image when credentials provided

**Depends on:** INFRA-3

---

### INFRA-5 — Frontend: Create Dockerfile for React + Vite App

**Task ID:** INFRA-5  
**Type:** INFRA setup  
**Plan:** Plan #43  
**Implements:** Frontend Image Build — Dockerfile

**Description:**  
Create `services/front-end/Dockerfile` for multi-stage build:
- **Stage 1 (builder):** Node.js base, install pnpm, build with `pnpm build`
- **Stage 2 (runtime):** Minimal web server (nginx or Node.js), serve built dist/

**Inputs:**
- Frontend source tree: `services/front-end/`
- Build script: `pnpm build` (from package.json)
- Output: `dist/` directory

**Outputs:**
- `services/front-end/Dockerfile` with multi-stage build
- Image runs optimized for production

**Acceptance criteria:**
- [ ] Dockerfile exists at `services/front-end/Dockerfile`
- [ ] Uses Node.js 20+ or 21+ (matches backend Java 21 era, recommend pnpm-compatible image)
- [ ] Stage 1 (builder): uses `node:20-alpine` or `node:21-alpine`
- [ ] Stage 1: installs pnpm via corepack: `corepack enable && corepack prepare`
- [ ] Stage 1: copies `package.json`, `pnpm-lock.yaml`
- [ ] Stage 1: runs `pnpm install --frozen-lockfile`
- [ ] Stage 1: copies full source tree
- [ ] Stage 1: runs `pnpm build`
- [ ] Stage 2 (runtime): uses `node:20-alpine` or lightweight nginx image
- [ ] Stage 2: copies dist from stage 1: `COPY --from=builder /app/dist /var/www/html`
- [ ] Dockerfile exposes port 3000 (or appropriate for app)
- [ ] Dockerfile includes CMD to start app (e.g., `npm start` or `npx serve dist`)
- [ ] Build succeeds: `docker build -t trade-lab-frontend:test services/front-end/`
- [ ] Image size reasonable (<500MB for node-based, <100MB for nginx-based)

**Depends on:** none

---

### INFRA-6 — Frontend: Configure Docker Build in Release Workflow

**Task ID:** INFRA-6  
**Type:** INFRA setup  
**Plan:** Plan #43  
**Implements:** Frontend Image Build — Workflow Integration

**Description:**  
Add frontend Docker image build step to `release.yml`. Uses Dockerfile from INFRA-5 to build and tag image with version, then push to GHCR.

**Inputs:**
- Dockerfile from INFRA-5: `services/front-end/Dockerfile`
- Version tag from INFRA-1 (e.g., `v1.0.0`)
- GHCR credentials from GitHub Actions secrets

**Outputs:**
- Built image: `ghcr.io/Desislav-Petrov/trade-lab-frontend:vX.Y.Z`
- Image pushed to GHCR

**Acceptance criteria:**
- [ ] Workflow job added to `release.yml` for frontend build
- [ ] Job uses GitHub's `docker/build-push-action` or equivalent
- [ ] Builds Dockerfile: `services/front-end/Dockerfile`
- [ ] Image tagged: `ghcr.io/Desislav-Petrov/trade-lab-frontend:vX.Y.Z` (version extracted from tag)
- [ ] Also tags with `latest`
- [ ] Pushes to GHCR using GitHub token (GITHUB_TOKEN with packages:write)
- [ ] Job runs in parallel with backend build (or sequentially, both succeed before publish)
- [ ] Workflow logs show build step, tagging, push confirmation
- [ ] If push fails, entire release workflow fails

**Depends on:** INFRA-2, INFRA-5

---

### INFRA-7 — Docker Compose: Local Runtime Composition

**Task ID:** INFRA-7  
**Type:** INFRA setup  
**Plan:** Plan #43  
**Implements:** Docker Compose Local Runtime

**Description:**  
Create `docker-compose.yml` at repository root for local development and release validation. Composes backend and frontend services using published GHCR images (not local builds).

**Inputs:**
- GHCR image URLs: `ghcr.io/Desislav-Petrov/trade-lab-backend:vX.Y.Z` and frontend equivalent
- Service dependencies: backend API, frontend web server
- Port configuration: backend (8080 or configured), frontend (3000 or configured)

**Outputs:**
- `docker-compose.yml` at project root
- Runnable local environment using released images

**Acceptance criteria:**
- [ ] File exists: `docker-compose.yml`
- [ ] Services defined: `backend`, `frontend`
- [ ] Backend service: image `ghcr.io/Desislav-Petrov/trade-lab-backend:${BACKEND_VERSION:-latest}`
- [ ] Frontend service: image `ghcr.io/Desislav-Petrov/trade-lab-frontend:${FRONTEND_VERSION:-latest}`
- [ ] Ports exposed: backend on 8080, frontend on 3000 (or as configured)
- [ ] Environment variables support version pinning via `.env` file or compose override
- [ ] Backend depends_on frontend or vice versa if needed (or omit if independent)
- [ ] Includes example `.env.example` showing how to set versions: `BACKEND_VERSION=v1.0.0`
- [ ] Compose file documentation comments explaining version pinning
- [ ] Can run: `docker-compose up -d` and access frontend at http://localhost:3000

**Depends on:** INFRA-4, INFRA-6

---

### INFRA-8 — GitHub Actions: Configure GHCR Authentication

**Task ID:** INFRA-8  
**Type:** INFRA setup  
**Plan:** Plan #43  
**Implements:** Publish Images — GHCR Authentication

**Description:**  
Configure GitHub Actions to authenticate with GHCR and publish images. Uses GitHub's built-in GITHUB_TOKEN with `packages:write` scope.

**Inputs:**
- GitHub token (provided by Actions automatically)
- Registry URL: `ghcr.io`
- Repository owner: `Desislav-Petrov`

**Outputs:**
- Workflow authenticated to GHCR
- Images pushed successfully
- No additional secrets required (GITHUB_TOKEN is built-in)

**Acceptance criteria:**
- [ ] `release.yml` includes step to authenticate with GHCR
- [ ] Uses `docker/login-action` or equivalent
- [ ] Registry: `ghcr.io`
- [ ] Username: `${{ github.actor }}`
- [ ] Password: `${{ secrets.GITHUB_TOKEN }}`
- [ ] Credentials only available during workflow execution
- [ ] Repository settings verified: Actions > General > Workflow permissions = "Read and write permissions"
- [ ] Backend Jib task configured to use GHCR token: environment variable `JLIB_TO_AUTH_HELPER_USERNAME` and `JLIB_TO_AUTH_HELPER_PASSWORD`
- [ ] Workflow step or gradle property injects token: `JLIB_TO_AUTH_HELPER_PASSWORD=${{ secrets.GITHUB_TOKEN }}`
- [ ] Authentication credentials not logged (masked in workflow output)

**Depends on:** INFRA-2, INFRA-4, INFRA-6

---

### INFRA-9 — GitHub Actions: Configure Release Notes Generation

**Task ID:** INFRA-9  
**Type:** INFRA setup  
**Plan:** Plan #43  
**Implements:** GitHub Release Creation

**Description:**  
Add release creation step to `release.yml`. Creates GitHub Release with auto-generated notes comparing current tag against previous tag, including merged PRs and commit history.

**Inputs:**
- Version tag: `vX.Y.Z` (from INFRA-1)
- GitHub token (for API access)
- Previous release tag (auto-detected by GitHub)

**Outputs:**
- GitHub Release created at https://github.com/Desislav-Petrov/trade-lab/releases/tag/vX.Y.Z
- Release notes auto-generated from PR titles and commits

**Acceptance criteria:**
- [ ] Release job added to `release.yml`
- [ ] Uses GitHub's `softprops/action-gh-release` or equivalent
- [ ] Release tag: `${{ github.ref_name }}` (e.g., `v1.0.0`)
- [ ] Generate release notes: `generate_release_notes: true`
- [ ] Compares against: previous release (auto-detected)
- [ ] Release notes include merged PRs with titles
- [ ] Release notes include commit history
- [ ] Release is marked as latest (not pre-release)
- [ ] No manual changelog required (fully automated)
- [ ] Recommend commit message convention in docs (conventional commits: `feat:`, `fix:`, `docs:`, etc.)

**Depends on:** INFRA-2, INFRA-8

---

### INFRA-10 — Repository: Update GitHub Actions Permissions

**Task ID:** INFRA-10  
**Type:** INFRA setup (manual verification)  
**Plan:** Plan #43  
**Implements:** Manual Setup Required — GitHub Actions Permissions

**Description:**  
Verify repository settings allow GitHub Actions to write packages (GHCR) and create releases.

**Inputs:**
- Repository: `Desislav-Petrov/trade-lab`
- Required permission: Actions > General > Workflow permissions = "Read and write permissions"

**Outputs:**
- Confirmed settings in repository configuration

**Acceptance criteria:**
- [ ] Navigate to: https://github.com/Desislav-Petrov/trade-lab/settings/actions
- [ ] Section: General
- [ ] Setting: "Workflow permissions"
- [ ] Verified: "Read and write permissions" is selected (not "Read repository contents permission")
- [ ] Verified: "Allow GitHub Actions to create and approve pull requests" is checked (optional, recommended)
- [ ] Screenshot or confirmation note added to implementation notes

**Depends on:** none (manual, but should be verified before running release.yml)

---

### INFRA-11 — Documentation: Release Process Guide for Developers

**Task ID:** INFRA-11  
**Type:** INFRA setup  
**Plan:** Plan #43  
**Implements:** Developer Documentation

**Description:**  
Create developer-facing documentation explaining the release process. Covers how to trigger a release, what happens automatically, and how to validate released artifacts locally.

**Inputs:**
- Workflow descriptions (from INFRA-1, INFRA-2)
- Docker Compose setup (from INFRA-7)
- Commit conventions (from INFRA-9)

**Outputs:**
- `RELEASE.md` or section in `README.md`
- Clear step-by-step guide for developers

**Acceptance criteria:**
- [ ] Documentation file created: `RELEASE.md` at repo root
- [ ] **Section 1: Manual Release Trigger**
  - [ ] Step-by-step: Open GitHub Actions → "Create Version" → Enter version → Run
  - [ ] Example: "v1.0.0"
- [ ] **Section 2: What Happens Automatically**
  - [ ] List automated steps: tag creation, image build, GHCR push, release creation
  - [ ] Expected duration
- [ ] **Section 3: Version Format**
  - [ ] Semantic versioning: X.Y.Z (major.minor.patch)
  - [ ] Examples: v0.1.0, v1.0.0, v1.2.3
- [ ] **Section 4: Local Validation**
  - [ ] Instructions to run docker-compose locally
  - [ ] How to pin versions: `.env` file setup
  - [ ] Verification steps: visit http://localhost:3000
- [ ] **Section 5: Commit Message Convention (Optional)**
  - [ ] Recommended format for better release notes: conventional commits
  - [ ] Examples: `feat: add user auth`, `fix: resolve login bug`, `docs: update readme`
- [ ] **Section 6: Troubleshooting**
  - [ ] Link to GitHub Actions logs
  - [ ] Common issues: GHCR auth, image build failures
  - [ ] How to manually trigger release if needed
- [ ] Document is clear, concise, and accessible to developers unfamiliar with the system

**Depends on:** INFRA-1 through INFRA-10

---

## Dependency Summary

| Task ID | Title | Depends On |
|---------|-------|-----------|
| INFRA-1 | Create GitHub Actions: Create Version Workflow | none |
| INFRA-2 | Create GitHub Actions: Release Pipeline Workflow | INFRA-1, INFRA-3, INFRA-4, INFRA-6 |
| INFRA-3 | Backend: Configure Jib Gradle Plugin | none |
| INFRA-4 | Backend: Create Jib Image Build Task | INFRA-3 |
| INFRA-5 | Frontend: Create Dockerfile for React + Vite App | none |
| INFRA-6 | Frontend: Configure Docker Build in Release Workflow | INFRA-2, INFRA-5 |
| INFRA-7 | Docker Compose: Local Runtime Composition | INFRA-4, INFRA-6 |
| INFRA-8 | GitHub Actions: Configure GHCR Authentication | INFRA-2, INFRA-4, INFRA-6 |
| INFRA-9 | GitHub Actions: Configure Release Notes Generation | INFRA-2, INFRA-8 |
| INFRA-10 | Repository: Update GitHub Actions Permissions | none (manual verification) |
| INFRA-11 | Documentation: Release Process Guide for Developers | INFRA-1 through INFRA-10 |

---

## Implementation Order

**Phase 1: Prerequisites** (can run in parallel)
- INFRA-1: Create Version Workflow
- INFRA-3: Jib Plugin Config
- INFRA-5: Frontend Dockerfile
- INFRA-10: Verify Actions Permissions

**Phase 2: Core Build & Push** (depends on Phase 1)
- INFRA-4: Jib Build Task
- INFRA-6: Frontend Docker Build in Workflow
- INFRA-8: GHCR Auth

**Phase 3: Workflows & Automation** (depends on Phase 2)
- INFRA-2: Release Pipeline Workflow
- INFRA-9: Release Notes

**Phase 4: Validation & Docs** (depends on Phase 3)
- INFRA-7: Docker Compose
- INFRA-11: Developer Docs

---

## Notes

- **Image naming:** All tasks use `ghcr.io/Desislav-Petrov/trade-lab-{backend,frontend}:vX.Y.Z` as specified
- **Version extraction:** Workflows extract version from tag (e.g., `v1.0.0` → version `1.0.0`)
- **No Dockerfile for backend:** Jib handles containerization without explicit Dockerfile
- **Frontend base:** Vite build is lightweight; nginx or node serve can run dist
- **Credentials:** All use GITHUB_TOKEN (no additional secrets needed)
- **Commit conventions:** Encouraged but not enforced; improves release notes quality

---

**End of Decomposition**
