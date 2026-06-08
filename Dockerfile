# Paso 1: Compilación con Java 25 usando el Maven Wrapper de tu proyecto
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app

# Copiar archivos esenciales de Maven de tu carpeta "native"
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Dar permisos al script y descargar dependencias
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline

# Copiar el código fuente (src/main/java/com/example/demo/*)
COPY src ./src

# Compilar y generar el archivo jar (native-0.0.1-SNAPSHOT.jar)
RUN ./mvnw clean package -DskipTests

# Paso 2: Imagen ligera de ejecución con Java 25
FROM eclipse-temurin:25-jre-noble
WORKDIR /app

# Buscamos dinámicamente cualquier jar en la carpeta target y lo renombramos a app.jar
COPY --from=build /app/target/*.jar app.jar

# Requisito obligatorio para el almacenamiento temporal
RUN mkdir -p /app/temporal_efs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]