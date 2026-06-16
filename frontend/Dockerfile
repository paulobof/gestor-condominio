# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine AS runtime
# Origem da API permitida na CSP, por ambiente (ex.: https://*.helbortrilogyhome.com.br).
# Vazio = apenas same-origin. Definir no Dokploy como build arg do servico frontend.
ARG CSP_API_ORIGIN=
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN sed -i "s|\${CSP_API_ORIGIN}|${CSP_API_ORIGIN}|g" /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
RUN apk add --no-cache curl
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -fs http://localhost/healthz || exit 1
