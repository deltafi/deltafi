# ABOUTME: Multi-stage Docker build for the DeltaFi documentation site.
# ABOUTME: Builds the VitePress site with npm and serves it via nginx.
# Build context must be the repo root: docker build -f deltafi-docs/Dockerfile -t deltafi/deltafi-docs:latest ..

FROM node:22-alpine AS build
WORKDIR /build/deltafi-docs

# Cache package installation
COPY deltafi-docs/package.json deltafi-docs/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

# Copy the docs source
COPY deltafi-docs/.vitepress ./.vitepress
COPY deltafi-docs/docs ./docs
COPY deltafi-docs/public ./public

# Copy files from outside deltafi-docs that the build references via relative paths
COPY CHANGELOG.md /build/CHANGELOG.md
COPY deltafi-core-actions/src/main/resources/docs/ /build/deltafi-core-actions/src/main/resources/docs/
COPY deltafi-core/src/main/resources/permissions.csv /build/deltafi-core/src/main/resources/permissions.csv

RUN npm run build

FROM nginx:alpine
COPY --from=build /build/deltafi-docs/.vitepress/dist /usr/share/nginx/html
EXPOSE 80
