FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built Spring Boot JAR
COPY target/flow-core-service-0.0.1-SNAPSHOT.jar app.jar

# FCS listens on 7070 (SERVER_PORT env overrides the default 8080 in application.yml)
EXPOSE 7070

# Health check — used by docker-compose depends_on: condition: service_healthy
HEALTHCHECK --interval=10s --timeout=5s --retries=10 \
  CMD wget -qO- http://localhost:7070/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

