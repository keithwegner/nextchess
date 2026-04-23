FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates stockfish \
    && rm -rf /var/lib/apt/lists/*

ENV PATH="/usr/games:${PATH}"

WORKDIR /app
COPY --from=builder /workspace/target/next-chess-desktop-java-*.jar /app/next-chess.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/next-chess.jar", "--no-browser", "--host=0.0.0.0", "--port=8080"]
