FROM eclipse-temurin:21-jdk

EXPOSE 3001
RUN mkdir -p /app/public
WORKDIR /app
COPY chp.jar .
COPY config.clj .
COPY public public
COPY src src
CMD java -Dconfig=config.clj -Dclojure.server.repl="{:port 5555 :accept clojure.core.server/repl}" -jar chp.jar

