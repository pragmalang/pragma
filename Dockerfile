FROM metacall/core:cli

USER root:root

WORKDIR /

COPY . /pragma

RUN apt-get --allow-releaseinfo-change update && apt-get install -y curl git && \
  git clone https://www.github.com/metacall/scala-port.git && \
  curl -fLo cs https://git.io/coursier-cli-"$(uname | tr LD ld)" && \
  chmod +x cs && \
  eval "$(./cs setup --env --jvm 11)" && \
  cs install sbt ammonite && \
  echo "export PATH=\"$PATH:/root/.local/share/coursier/bin\"" >> /root/.bashrc && \
  eval "$(cat /root/.bashrc)" && \
  METACALL_VARS="$(amm -s /pragma/scripts/write-metacall-env-vars.sc)" && \
  echo $METACALL_VARS >> /root/.bashrc && \
  cat /root/.bashrc && \
  cd /pragma && sbt "compile;Test/compile"

ENTRYPOINT [ "/bin/bash" ]