# One image with the API and the web app. Data lives in the /data volume.
FROM node:22-slim AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml tsconfig.base.json ./
COPY packages/core/package.json packages/core/
COPY apps/api/package.json apps/api/
COPY apps/web/package.json apps/web/
RUN pnpm install --frozen-lockfile
COPY packages packages
COPY apps/api apps/api
COPY apps/web apps/web
RUN pnpm build
# The server is bundled into one file; only native modules (SQLite) come from node_modules.
RUN pnpm --filter @split/api deploy --prod --legacy /out

FROM node:22-slim
ENV NODE_ENV=production PORT=8787 DATABASE_PATH=/data/split.db WEB_DIST=/srv/web
WORKDIR /srv/api
COPY --from=build /out/node_modules ./node_modules
COPY --from=build /app/apps/api/dist ./dist
COPY --from=build /app/apps/web/dist /srv/web
RUN mkdir /data && chown node:node /data
VOLUME /data
EXPOSE 8787
USER node
CMD ["node", "dist/server.js"]
