# Release Process

This document describes how to create and manage releases of the Trade Lab platform.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Release Workflow](#release-workflow)
3. [Version Format](#version-format)
4. [Local Validation](#local-validation)
5. [Commit Message Conventions](#commit-message-conventions)
6. [Troubleshooting](#troubleshooting)

---

## Quick Start

### How to Create a Release

1. **Open GitHub Actions**
   - Navigate to: https://github.com/Desislav-Petrov/trade-lab/actions

2. **Select "Create Version" Workflow**
   - Look for the workflow named **"Create Version"** in the left sidebar

3. **Run Workflow**
   - Click **"Run workflow"** button
   - A dropdown will appear

4. **Enter Version Number**
   - Input version in semantic format: `X.Y.Z`
   - Example: `1.0.0`
   - ⚠️ Do NOT include the `v` prefix — the workflow adds it automatically

5. **Confirm and Run**
   - Click **"Run workflow"** button
   - The workflow will execute and create the release

6. **Monitor Release Pipeline**
   - The "Release Pipeline" workflow will trigger automatically
   - Watch the Actions page to see:
     - Backend Docker image build ✅
     - Frontend Docker image build ✅
     - Images pushed to GHCR ✅
     - GitHub release created ✅

---

## Release Workflow

### What Happens Automatically

Once you trigger "Create Version":

1. **Version Tag Created**
   - Git tag `vX.Y.Z` is created and pushed to the repository

2. **Release Pipeline Triggered**
   - Automatically runs on tag push
   - No further action needed

3. **Backend Image Built**
   - Spring Boot application packaged as Docker image
   - Image: `ghcr.io/desislav-petrov/trade-lab-backend:vX.Y.Z`
   - Pushed to GitHub Container Registry (GHCR)

4. **Frontend Image Built**
   - React/Vite application packaged as Docker image
   - Image: `ghcr.io/desislav-petrov/trade-lab-frontend:vX.Y.Z`
   - Pushed to GHCR

5. **GitHub Release Created**
   - Release page auto-generated at: `/releases/tag/vX.Y.Z`
   - Release notes automatically generated from:
     - Merged pull requests
     - Commit history since last release
     - No manual changelog required

### Timeline

- **Manual trigger** → ~1 min
- **Backend build** → ~3-5 min
- **Frontend build** → ~2-3 min
- **Image push** → ~1 min
- **Release creation** → ~1 min
- **Total** → ~10 min (estimated)

---

## Version Format

Use **Semantic Versioning** (SemVer): `MAJOR.MINOR.PATCH`

### Examples

| Version | Use Case |
|---------|----------|
| `0.1.0` | Initial development release |
| `1.0.0` | First production release |
| `1.0.1` | Patch: bug fix, no new features |
| `1.1.0` | Minor: new backward-compatible feature |
| `2.0.0` | Major: breaking changes |

### Guidelines

- **MAJOR** → Breaking API changes, major refactors
- **MINOR** → New features (backward compatible)
- **PATCH** → Bug fixes, small improvements

---

## Local Validation

### Prerequisites

- Docker and Docker Compose installed
- ~2GB free disk space for images

### Step 1: Set Up Environment Variables

Create a `.env` file from the template:

```bash
cp .env.example .env
```

Edit `.env` to pin versions:

```env
BACKEND_VERSION=1.0.0
FRONTEND_VERSION=1.0.0
```

Or use `latest` for the newest release:

```env
BACKEND_VERSION=latest
FRONTEND_VERSION=latest
```

### Step 2: Start the Application

```bash
docker-compose up -d
```

### Step 3: Verify Services

**Backend (API Server)**
- URL: http://localhost:8080
- Health check: http://localhost:8080/actuator/health
- Admin UI: http://localhost:8080/admin

**Frontend (Web UI)**
- URL: http://localhost:3000
- Should load the Trade Lab application

### Step 4: View Logs

```bash
# All services
docker-compose logs -f

# Backend only
docker-compose logs -f backend

# Frontend only
docker-compose logs -f frontend
```

### Step 5: Stop Services

```bash
docker-compose down
```

### Full Example: Local Release Validation

```bash
# Set up environment
cp .env.example .env
echo "BACKEND_VERSION=1.0.0" > .env
echo "FRONTEND_VERSION=1.0.0" >> .env

# Start application
docker-compose up -d

# Wait for services to be healthy
sleep 30

# Check health
curl http://localhost:8080/actuator/health
curl http://localhost:3000

# View logs
docker-compose logs

# Stop when done
docker-compose down
```

---

## Commit Message Conventions

### Recommended: Conventional Commits

Use this format for better release notes:

```
<type>: <description>
```

### Types

| Type | Description | Release Impact |
|------|-------------|-----------------|
| `feat:` | New feature | MINOR version bump |
| `fix:` | Bug fix | PATCH version bump |
| `docs:` | Documentation only | No version change |
| `refactor:` | Code refactor (no logic change) | No version change |
| `perf:` | Performance improvement | MINOR version bump |
| `chore:` | Maintenance, dependencies | No version change |
| `ci:` | CI/CD changes | No version change |
| `test:` | Test additions/changes | No version change |

### Examples

✅ Good:
```
feat: add user portfolio dashboard
fix: resolve login timeout issue
docs: update API documentation
chore: upgrade Spring Boot to 4.1.0
```

❌ Avoid:
```
update code
fix bug
changes
```

### Why It Matters

Better commit messages → Better release notes → Clearer changelog for users

---

## Troubleshooting

### Issue: Workflow Fails with "Invalid Version Format"

**Cause:** Version number doesn't match `X.Y.Z` format

**Solution:**
- ✅ Correct: `1.0.0`, `0.1.0`, `2.5.3`
- ❌ Incorrect: `v1.0.0`, `1.0`, `1.0.0.0`

Do NOT include `v` — the workflow adds it automatically.

---

### Issue: Backend Image Build Fails

**Common Causes:**
1. Gradle build fails (code compilation error)
2. JVM memory limit exceeded
3. Network issue pulling base image

**Solution:**
1. Check GitHub Actions logs for the error message
2. Verify all tests pass locally: `cd services/backend && ./gradlew clean build`
3. Retry the workflow

---

### Issue: Frontend Image Build Fails

**Common Causes:**
1. npm/pnpm dependency resolution fails
2. TypeScript compilation error
3. Build script error in `package.json`

**Solution:**
1. Check logs for error details
2. Verify locally: `cd services/front-end && pnpm install && pnpm build`
3. Fix and push to main, then create release again

---

### Issue: Can't Access Released Image Locally

**Cause:** Docker daemon not logged in to GHCR

**Solution:**

```bash
# Log in to GHCR (one time)
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USER --password-stdin

# Or use personal access token if GITHUB_TOKEN not available
docker login ghcr.io
# Username: your-github-username
# Password: your-personal-access-token
```

---

### Issue: Images Not Pushed to GHCR

**Cause:** Repository Actions permissions not configured

**Solution:**
1. Go to: https://github.com/Desislav-Petrov/trade-lab/settings/actions
2. Under **General** → **Workflow permissions**
3. Select **"Read and write permissions"** (not just "Read repository contents")
4. Save changes
5. Retry the release workflow

---

### Issue: Release Notes Not Generated

**Cause:** No previous release tag exists, or GitHub API rate limit exceeded

**Solution:**
1. Release notes will be empty for the first release — this is normal
2. All subsequent releases will have proper notes
3. Wait a few minutes if rate-limited, then retry

---

## Manual Release (If Workflow Fails)

If the automated workflow fails and you need to release manually:

1. **Create tag locally:**
   ```bash
   git tag -a v1.0.0 -m "Release v1.0.0"
   git push origin v1.0.0
   ```

2. **Push backend image manually:**
   ```bash
   cd services/backend
   ./gradlew jib -PimageVersion=v1.0.0
   ```

3. **Push frontend image manually:**
   ```bash
   cd services/front-end
   docker build -t ghcr.io/desislav-petrov/trade-lab-frontend:v1.0.0 .
   docker push ghcr.io/desislav-petrov/trade-lab-frontend:v1.0.0
   ```

4. **Create GitHub release manually:**
   - Go to: https://github.com/Desislav-Petrov/trade-lab/releases
   - Click **"Draft a new release"**
   - Select tag `v1.0.0`
   - Enter release notes
   - Publish

---

## FAQ

### Q: Can I test a release without triggering the full pipeline?

**A:** Yes, you can build images locally:

```bash
# Backend
cd services/backend
./gradlew jib -PimageVersion=test-local -Djib.console=plain

# Frontend
cd services/front-end
docker build -t trade-lab-frontend:test-local .
```

---

### Q: Can I undo a release?

**A:** Yes, but it requires manual cleanup:

1. Delete the GitHub release (go to Releases page)
2. Delete the git tag: `git tag -d v1.0.0 && git push origin :refs/tags/v1.0.0`
3. Delete images from GHCR (go to Packages page)

Then create a new release with a different version.

---

### Q: How long does a release take?

**A:** Typically 8-12 minutes end-to-end:
- Backend build: 3-5 min
- Frontend build: 2-3 min
- Push + release creation: 2-3 min

---

### Q: Can I release multiple versions at once?

**A:** No. Releases are sequential. Wait for one to complete before starting the next.

---

## Support

For issues or questions:

1. Check the **Troubleshooting** section above
2. Review GitHub Actions logs: https://github.com/Desislav-Petrov/trade-lab/actions
3. Create an issue: https://github.com/Desislav-Petrov/trade-lab/issues

---

**Last Updated:** July 2026
