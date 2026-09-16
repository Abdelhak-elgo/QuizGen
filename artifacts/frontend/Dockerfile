FROM node:20-bookworm-slim AS builder

WORKDIR /workspace

RUN npm install --global pnpm@10.26.1

COPY . .

RUN pnpm install --filter @workspace/frontend... --frozen-lockfile

ARG BASE_PATH=/
ARG PORT=4173
ARG VITE_KEYCLOAK_URL=http://localhost:8180
ARG VITE_KEYCLOAK_REALM=quizgen
ARG VITE_KEYCLOAK_CLIENT_ID=quizgen-frontend

ENV BASE_PATH=${BASE_PATH}
ENV PORT=${PORT}
ENV NODE_ENV=production
ENV VITE_KEYCLOAK_URL=${VITE_KEYCLOAK_URL}
ENV VITE_KEYCLOAK_REALM=${VITE_KEYCLOAK_REALM}
ENV VITE_KEYCLOAK_CLIENT_ID=${VITE_KEYCLOAK_CLIENT_ID}

RUN pnpm --filter @workspace/frontend run build

FROM nginx:1.27-alpine

COPY artifacts/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/artifacts/frontend/dist/public /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
