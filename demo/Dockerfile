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

# Verify both installed during build
RUN ffmpeg -version && libreoffice --version

COPY . .

RUN mvn clean package -DskipTests

EXPOSE 8080

# ✅ Optimized JVM flags for 512MB Render free tier
CMD ["java", \
     "-Xmx420m", \
     "-Xms64m", \
     "-XX:+UseG1GC", \
     "-XX:MaxGCPauseMillis=200", \
     "-XX:+UseStringDeduplication", \
     "-XX:MetaspaceSize=64m", \
     "-XX:MaxMetaspaceSize=128m", \
     "-jar", "target/demo-0.0.1-SNAPSHOT.jar"]