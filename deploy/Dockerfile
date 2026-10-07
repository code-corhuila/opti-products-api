# Reproducible build: dependencies are resolved from the poms, tests run in CI (ci.yml).
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY products-core/pom.xml products-core/
COPY products-adapters/pom.xml products-adapters/
COPY products-app/pom.xml products-app/
COPY . .
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/products-app/target/products-api.jar app.jar
# Pre-create the frame photos directory owned by "app": Docker seeds a fresh named volume from
# whatever is already here, so the mount keeps this ownership instead of defaulting to root.
RUN mkdir -p /data/frame-images && chown -R app:app /data/frame-images
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
