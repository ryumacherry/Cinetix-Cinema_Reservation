# Stage 1: build (Gradle + JDK 17)
FROM gradle:8.10.2-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle installDist --no-daemon -x test

# Stage 2: run (hanya JRE, image lebih kecil)
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Jakarta
COPY --from=build /app/build/install/cinetix-api/ ./
EXPOSE 8080
CMD ["bin/cinetix-api"]
