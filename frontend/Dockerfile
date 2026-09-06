# Frontend image: build the static bundle, then serve it from nginx.
#
# nginx also proxies /api to the backend, which is what keeps the browser making
# same-origin requests in production exactly as it does behind the Vite dev server.
# No CORS configuration exists on the Spring Boot side and none is needed.

FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build


FROM nginx:1.27-alpine AS runtime

# The image's entrypoint runs envsubst over templates/. The filter keeps it to FAPP_*
# variables so nginx's own $uri and $host are left alone.
ENV FAPP_BACKEND_URL=http://backend:8080
# Docker's embedded DNS. Override when the platform resolves service names elsewhere.
ENV FAPP_DNS_RESOLVER=127.0.0.11
ENV NGINX_ENVSUBST_FILTER=FAPP_

COPY --from=build /build/dist/ /usr/share/nginx/html/
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 8080

# 127.0.0.1, not localhost: nginx listens on IPv4 only, and inside the container
# localhost resolves to ::1 first, which would make every probe fail.
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
