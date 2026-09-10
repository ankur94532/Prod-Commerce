# syntax=docker/dockerfile:1.7
# The built SPA, served by nginx.
#
# The frontend had no container and no Kubernetes manifest at all: it ran only as a Vite dev
# server from the README. So `kubectl apply -k k8s/` brought up nine backend services and
# nothing a shopper could open.
FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Empty on purpose, and not the same as unset. The gateway sits behind the same ingress host,
# so an empty base makes every API call a same-origin relative path: no CORS, and no hostname
# compiled into the bundle, which is what lets one image serve staging and production alike.
# See frontend/src/api/apiBase.js, which reads this with ?? rather than ||.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
# The unprivileged image listens on 8080 and runs as uid 101 already, so the container needs
# no root and no capability to bind a low port.
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080
