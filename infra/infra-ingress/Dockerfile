FROM node:24-alpine AS client-build

WORKDIR /app
COPY client-service/package*.json ./
RUN npm ci
COPY client-service/ ./
RUN npm run build

FROM node:24-alpine AS admin-build

WORKDIR /repo/admin-client-service
COPY admin-client-service/package*.json ./
RUN npm ci
COPY admin-client-service/ ./
RUN npm run build

FROM nginx:stable-alpine
COPY infra/infra-ingress/nginx-prod.conf /etc/nginx/conf.d/default.conf
COPY infra/infra-ingress/proxy-headers.inc /etc/nginx/proxy-headers.inc
COPY --from=client-build /app/dist /usr/share/nginx/html
COPY --from=admin-build /repo/admin-client-service/dist /usr/share/nginx/html/admin

EXPOSE 80
