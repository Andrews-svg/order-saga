# Stage 1: build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY product-service/build.gradle.kts product-service/
COPY order-service/build.gradle.kts order-service/
COPY inventory-service/build.gradle.kts inventory-service/
COPY notification-service/build.gradle.kts notification-service/

RUN chmod +x gradlew && ./gradlew help --no-daemon

COPY order-service/src order-service/src
RUN ./gradlew :order-service:bootJar --no-daemon

# Stage 2: runtime
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/order-service/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]