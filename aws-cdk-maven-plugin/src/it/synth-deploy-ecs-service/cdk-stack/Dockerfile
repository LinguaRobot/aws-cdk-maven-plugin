FROM openjdk:11-jre-slim
MAINTAINER Roman Kishchenko <rkishchenko@linguarobot.io>

ARG REST_SERVICE_APP

COPY $REST_SERVICE_APP rest-service-app.jar

ENTRYPOINT ["java", "-jar", "rest-service-app.jar"]
