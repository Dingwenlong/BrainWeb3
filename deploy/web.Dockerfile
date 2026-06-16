# BrainWeb3 web edge — builds the Vue SPA, then serves it + proxies /api via Caddy.
# Build context: repository root (needs apps/frontend and deploy/Caddyfile).
# ---- build the SPA ----
FROM node:20-alpine AS build
WORKDIR /app
# Build the frontend standalone (avoids installing the contracts/hardhat workspace)
COPY apps/frontend/package.json ./package.json
RUN npm install
COPY apps/frontend/ ./
RUN npm run build

# ---- serve with Caddy (TLS + SPA + /api proxy) ----
FROM caddy:2-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
EXPOSE 80 443
