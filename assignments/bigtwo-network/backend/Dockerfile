FROM maven:3.10.1-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copy pom and download dependencies first to leverage layer caching
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/target/*.jar ./app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms128m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar"]
