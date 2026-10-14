FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
ENV JAVA_OPTS="-Xmx350m" THYMELEAF_CACHE=true COOKIE_SECURE=true
EXPOSE 8080
CMD ["sh","-c","java $JAVA_OPTS -jar app.jar"]
