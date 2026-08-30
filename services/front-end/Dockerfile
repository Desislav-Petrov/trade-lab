# ── Stage 1: Builder ──────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

# Enable corepack for pnpm
RUN npm install -g pnpm

# Copy dependency files
COPY package.json pnpm-lock.yaml ./

# Install dependencies with frozen lockfile (reproducible builds)
RUN pnpm install --frozen-lockfile --prefer-offline

# Copy source code
COPY . .

# Build the frontend
RUN pnpm run build

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM nginx:1.27-alpine

# Copy built SPA
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx config (proxies /api/* and WebSocket upgrades to the backend)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q --spider http://localhost:3000 || exit 1

# Metadata labels
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION=unknown

LABEL org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${VERSION}"
