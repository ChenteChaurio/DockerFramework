FROM openjdk:17-jdk-slim
WORKDIR /usrapp/bin
ENV PORT=8080

COPY SpringBoot/target/classes /usrapp/bin/classes
COPY SpringBoot/target/dependency /usrapp/bin/dependency

CMD ["java","-cp","./classes:./dependency/*","edu.escuelaing.arep.App"]