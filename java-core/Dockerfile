FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY certs/russiantrustedca.pem /tmp/russiantrustedca.pem
RUN keytool -importcert -noprompt -trustcacerts \
    -alias russian-trusted-ca \
    -file /tmp/russiantrustedca.pem \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit && \
    rm /tmp/russiantrustedca.pem

COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]