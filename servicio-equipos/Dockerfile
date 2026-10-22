# Imagen a partir del jar ya compilado (el host necesita solo Docker; no requiere BuildKit/buildx).
# Pasos (desde la RAIZ del monorepo):
#   mvn -B -DskipTests package
#   docker build -f servicio-equipos/Dockerfile -t biohub/servicio-equipos .
# deploy/desplegar.sh hace ambos pasos.
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --chown=app:app servicio-equipos/target/servicio-equipos-*.jar /app/app.jar
USER app
ENV SERVER_ADDRESS=0.0.0.0 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8082
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
