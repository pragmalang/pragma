FROM debian:latest as scala-env

WORKDIR /tmp/
RUN apt-get -y update && \
    apt-get -y install bash git curl make lsof net-tools rsync

RUN curl -Lo cs https://git.io/coursier-cli-linux && \
    chmod +x cs && \
    eval "$(./cs setup --env --jvm 11 --apps sbt-launcher,ammonite --install-dir /tmp/)"

ENV JAVA_HOME=/root/.cache/coursier/jvm/adopt@1.11.0-7
ENV PATH="$PATH:$JAVA_HOME/bin:/tmp/"

COPY . /home/pragma
WORKDIR /home/pragma
ENTRYPOINT [ "/bin/bash" ]

# Building pragma daemon and openwhisk
FROM scala-env

COPY --from=library/docker:latest /usr/local/bin/docker /bin/docker
COPY --from=scala-env /home/pragma /tmp/pragma
RUN curl -L "https://github.com/docker/compose/releases/download/1.27.4/docker-compose-$(uname -s)-$(uname -m)" -o /bin/docker-compose && \
    chmod +x /bin/docker-compose
WORKDIR /tmp/pragma/daemon/src/main/resources/
# RUN sbt "daemon/docker:publishLocal"
RUN echo "echo 'Running OpenWhisk...' && make quick-start && sbt 'daemon/docker:publishLocal' && docker run pragmad:latest -d && rm -rf /tmp/ /root/.cache/ openwhisk-src/" | tee run.sh
VOLUME [ "/var/run/docker.sock", "/root/" ]


# For Pragma daemon:
# EXPOSE 3030
# # For OpenWhisk:
# # - API Gateway
# EXPOSE 80
# EXPOSE 443
# EXPOSE 9000
# EXPOSE 9001
# EXPOSE 9090
# # - Redis
# EXPOSE 6379
# # - Zookeeper
# EXPOSE 2181 
# # - CouchDB
# EXPOSE 5984
# # - OpenWhisk's Invoker
# EXPOSE 8085
# EXPOSE 9333 
# # - OpenWhisk's Controller
# EXPOSE 8888
# EXPOSE 9222
# # - Kafka
# EXPOSE 9092
# # - Kafka Topics UI
# EXPOSE 8001

CMD [ "./run.sh" ]
