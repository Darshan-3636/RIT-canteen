FROM gradle:9.3.1-jdk17 AS build
WORKDIR /workspace
COPY --chown=gradle:gradle gradlew settings.gradle.kts build.gradle.kts ./
COPY --chown=gradle:gradle gradle/ ./gradle/
RUN gradle --no-daemon --max-workers=1 resolveRuntimeDependencies
COPY --chown=gradle:gradle src/ ./src/
RUN gradle --no-daemon --max-workers=1 installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/build/install/rit-canteen-backend/ ./
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAM=384m -Xms64m -Xmx224m -XX:MaxMetaspaceSize=112m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m -Xss512k -XX:ActiveProcessorCount=2 -XX:+UseSerialGC"
ENV DB_POOL_SIZE=2
EXPOSE 10000
CMD ["./bin/rit-canteen-backend"]
