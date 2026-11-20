FROM node:22-slim

WORKDIR /app

# Install build tools for native addons (better-sqlite3 needs them)
RUN apt-get update && apt-get install -y \
    python3 \
    make \
    g++ \
    && rm -rf /var/lib/apt/lists/*

COPY package.json ./

RUN npm install --omit=dev

COPY . .

EXPOSE 3000

CMD ["node", "server/index.js"]
