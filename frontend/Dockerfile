# ── Angular Frontend Dockerfile ───────────────────────────────
# Multi-stage: Node (build) → Nginx (serve)

FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --silent
COPY . .
RUN npm run build -- --configuration production

FROM nginx:1.25-alpine
# Remove default config
RUN rm /etc/nginx/conf.d/default.conf
# Add our config
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Copy Angular build output
COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
