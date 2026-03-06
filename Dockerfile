FROM clojure:temurin-21-tools-deps
# Install Node.js
RUN apt-get update && apt-get install -y curl ca-certificates && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm install
COPY shadow-cljs.edn deps.edn config.clj ./
COPY src/clj src/clj
COPY src/cljc src/cljc
COPY src/cljs src/cljs
COPY public/ public/
# Compile frontend using cljs alias
RUN clj -M:cljs -m shadow.cljs.devtools.cli compile main-prod grid-prod
# Prepare backend
EXPOSE 3001
CMD ["clj", "-J-Ddatomic.encryptChannel=false", "-M:server"]