FROM openjdk:21-ea
WORKDIR /usrapp/bin
ENV PORT 6000

COPY SpringBoot/target/classes /usrapp/bin/classes
COPY SpringBoot/target/dependency /usrapp/bin/dependency

CMD ["java","-cp","./classes:./dependency/*","edu.escuelaing.arep.App"]