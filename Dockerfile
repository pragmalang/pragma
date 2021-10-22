FROM debian

USER root:root

WORKDIR /

COPY . /pragma
RUN apt-get --allow-releaseinfo-change update && apt-get install -y curl bash git && \
  curl -fLo install-metacall.sh https://raw.githubusercontent.com/metacall/install/master/install.sh && \
  chmod +x ./install-metacall.sh && \
  ./install-metacall.sh && \
  curl -fLo cs https://git.io/coursier-cli-"$(uname | tr LD ld)" && \
  chmod +x cs && \
  eval "$(./cs setup --env --jvm 11 --apps sbt-launcher,ammonite)" && \
  eval "$(./cs install sbt)" && \
  echo "export PATH=\"$PATH:/root/.local/share/coursier/bin\"" > /root/.bashrc && \
  amm /pragma/scripts/write-metacall-env-vars.sc >> /root/.bashrc

WORKDIR /pragma

RUN sbt "compile;Test/compile"

ENTRYPOINT [ "/bin/bash" ]