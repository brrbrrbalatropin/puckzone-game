# Etapa 1: compilar el jar con JDK 21 (Lombok requiere 21, no 25)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Primero solo el descriptor y el wrapper para cachear las dependencias
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B --no-transfer-progress

COPY src ./src
RUN ./mvnw package -DskipTests -B --no-transfer-progress

# Etapa 2: imagen final solo con el JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
