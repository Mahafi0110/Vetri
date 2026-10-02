FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /app

# Install Python, pip, FFmpeg, and LibreOffice
RUN apt-get update && \
    apt-get install -y \
    python3 \
    python3-pip \
    python3-venv \
    ffmpeg \
    libreoffice \
    libreoffice-writer \
    && apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install Python conversion dependencies globally for the container
RUN pip3 install --no-cache-dir pdf2docx pymupdf --break-system-packages

# Fail the build early if a tool is missing
RUN ffmpeg -version && ffprobe -version && libreoffice --version && python3 --version

# Trim and Extract Audio need these FFmpeg encoders. If one is missing,
# the build stops here with a clear message instead of failing later for users.
RUN ffmpeg -hide_banner -encoders > /tmp/encoders.txt 2>&1; \
    for e in libx264 libvpx-vp9 libopus libvorbis libmp3lame; do \
      grep -q " $e " /tmp/encoders.txt || { echo "Missing FFmpeg encoder: $e"; exit 1; }; \
    done; \
    rm -f /tmp/encoders.txt

# Copy pom.xml first and download dependencies in their own step.
# Docker keeps this step cached, so code-only changes rebuild much faster.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY . .

RUN mvn clean package -DskipTests -B

EXPOSE 8080

# Leaves real headroom for LibreOffice's separate soffice process
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