FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy prebuilt jar (make sure you've run `mvn package` locally)
# The build currently produces `mercado-renata-1.0.0.jar`.
COPY target/mercado-renata-1.0.0.jar /app/app.jar

# Persistent H2 data
VOLUME ["/app/data"]

ENV GEMINI_API_KEY=""

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
