# ── Stage 1: Builder ──────────────────────────────────────────────────────────
FROM node:21-alpine AS builder

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
FROM node:21-alpine

WORKDIR /app

# Install serve globally (lightweight HTTP server for serving SPA)
RUN npm install -g serve

# Copy built artifacts from builder stage
COPY --from=builder /app/dist ./dist

# Expose port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD node -e "require('http').get('http://localhost:3000', (r) => {if (r.statusCode !== 200) throw new Error(r.statusCode)})"

# Metadata labels
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION=unknown

LABEL org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${VERSION}"

# Start server
CMD ["serve", "-s", "dist", "-l", "3000"]
