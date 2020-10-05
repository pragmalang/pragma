FROM debian:latest as scala-env

WORKDIR /tmp/
RUN apt-get -y update && \
    apt-get -y install bash git curl make && \
    curl -Lo cs https://git.io/coursier-cli-linux && \
    chmod +x cs && \
    eval "$(./cs setup --env --jvm 11 --apps sbt-launcher,ammonite --install-dir /tmp/)"


ENV JAVA_HOME=/root/.cache/coursier/jvm/adopt@1.11.0-7
ENV PATH="$PATH:$JAVA_HOME/bin:/tmp/"

COPY . /home/pragma
WORKDIR /home/pragma
ENTRYPOINT [ "/bin/bash" ]

# Building pragma daemon and openwhisk
FROM scala-env
VOLUME "/var/run/docker.sock"
COPY --from=library/docker:latest /usr/local/bin/docker /usr/bin/docker
COPY --from=docker/compose:latest /usr/local/bin/docker-compose /usr/bin/docker-compose
COPY --from=scala-env /home/pragma/ /tmp/pragma

WORKDIR /tmp/pragma
RUN sbt "project daemon;docker:publishLocal"

WORKDIR /tmp/pragma/daemon/src/main/resources/
RUN make quick-start && rm -rf /tmp/ /root/.cache/ ./openwhisk-src

EXPOSE 3030

CMD echo "Pragma environment is ready for use."
