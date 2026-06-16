# BrainWeb3 backend — Spring Boot 3.3.5 / Java 17 (build context: ./apps/backend)
# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Cache dependencies first (pom only) for faster rebuilds
COPY pom.xml ./
RUN mvn -q -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests clean package

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser \
 && mkdir -p /opt/brainweb3/storage \
 && chown -R appuser /opt/brainweb3
COPY --from=build /app/target/backend-0.1.0-SNAPSHOT.jar /app/app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
# Liveness: TCP connect to the HTTP port (bash is present in the temurin image)
HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=10 \
  CMD bash -c 'echo > /dev/tcp/127.0.0.1/8080' || exit 1
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
