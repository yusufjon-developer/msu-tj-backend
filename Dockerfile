FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY . .
# Ensure gradlew has execution permissions
RUN chmod +x ./gradlew
RUN ./gradlew bootJar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Copy the built jar from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar
# Copy the service account key (required for Firebase)
COPY serviceAccountKey.json /app/serviceAccountKey.json

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
