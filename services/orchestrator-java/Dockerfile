FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml ./
RUN mkdir -p src
RUN mvn -q -B dependency:go-offline
COPY . .
RUN mvn -q -B package -DskipTests
EXPOSE 8089
CMD ["java", "-jar", "target/orchestrator-java-1.0.0.jar"]
