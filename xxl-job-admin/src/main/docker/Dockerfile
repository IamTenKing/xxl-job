FROM openjdk:8
VOLUME /tmp
ADD xxl-job.jar /app.jar
EXPOSE 7705
ENTRYPOINT exec java -Djava.security.egd=file:/dev/./urandom -Dlogging.path=/data/logs $JAVA_OPTS  -jar /app.jar
