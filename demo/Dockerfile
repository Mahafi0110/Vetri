FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /app

# Install FFmpeg, LibreOffice, and essential core fonts for rendering layouts accurately
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    ffmpeg \
    libreoffice \
    libreoffice-writer \
    fonts-dejavu-core \
    fonts-libertine \
    fontconfig \
    && apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copy project files
COPY . .

# Build the application
RUN mvn clean package -DskipTests

EXPOSE 8080

# Optimized JVM flags for 512MB Render free tier with headless LibreOffice environment config
CMD ["sh", "-c", "java \
     -Xmx380m \
     -Xms64m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UseStringDeduplication \
     -XX:MetaspaceSize=64m \
     -XX:MaxMetaspaceSize=128m \
     -jar target/*.jar"]