# 1. Build stage (using Liberica JDK 24 on Debian)
FROM bellsoft/liberica-openjdk-debian:24 AS builder
WORKDIR /app

# Install minimal tools for sbt
RUN apt-get update && apt-get install -y --no-install-recommends curl gnupg ca-certificates && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add - && \
    apt-get update && apt-get install -y --no-install-recommends sbt && \
    rm -rf /var/lib/apt/lists/*

COPY . .
RUN sbt "project ingestionService" assembly

# 2. Run stage (using Liberica JRE 24 on Debian)
FROM bellsoft/liberica-openjre-debian:24
WORKDIR /app
COPY --from=builder /app/services/ingestion-service/target/scala-3.7.4/ingestion-service-assembly.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
