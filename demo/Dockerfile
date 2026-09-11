FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /app

# Install FFmpeg + LibreOffice
RUN apt-get update && \
    apt-get install -y \
    ffmpeg \
    libreoffice \
    libreoffice-writer \
    && apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN ffmpeg -version && libreoffice --version

COPY . .

RUN mvn clean package -DskipTests

EXPOSE 8080

# ✅ Leaves real headroom for LibreOffice's separate soffice process
CMD ["java", \
     "-Xmx200m", \
     "-Xms64m", \
     "-Xss512k", \
     "-XX:+UseG1GC", \
     "-XX:MaxGCPauseMillis=200", \
     "-XX:+UseStringDeduplication", \
     "-XX:MetaspaceSize=48m", \
     "-XX:MaxMetaspaceSize=96m", \
     "-XX:ReservedCodeCacheSize=48m", \
     "-XX:MaxDirectMemorySize=32m", \
     "-jar", "target/demo-0.0.1-SNAPSHOT.jar"]