FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml ./
RUN mkdir -p src
RUN mvn -q -B dependency:go-offline
COPY . .
RUN mvn -q -B package -DskipTests
EXPOSE 8081
CMD ["java", "-jar", "target/auth-service-0.1.0.jar"]
