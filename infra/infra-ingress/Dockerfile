FROM node:24-alpine AS client-build

WORKDIR /app
COPY client-service/package*.json ./
RUN npm ci
COPY client-service/ ./
RUN npm run build

FROM nginx:stable-alpine
COPY infra/infra-ingress/nginx-prod.conf /etc/nginx/conf.d/default.conf
COPY infra/infra-ingress/proxy-headers.inc /etc/nginx/proxy-headers.inc
COPY --from=client-build /app/dist /usr/share/nginx/html

EXPOSE 80
