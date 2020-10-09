#!/bin/bash
# Start the Pragma daemon and Openwhisk for local development
# NOTE: Requires Docker to be installed

IMAGE="${1:-openwhisk/standalone:nightly}"
shift
docker run --rm -d \
  --name openwhisk \
  -p 3233:3233 -p 3232:3232 -p 3234:3234 -p 8080:8080 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "$IMAGE" "$@"
docker exec openwhisk waitready

docker run -d --rm \
  -e POSTGRES_USER=test \
  -e POSTGRES_PASSWORD=test \
  -e POSTGRES_DB=test \
  -p 5432:5432 \
  postgres:latest

docker run -it --rm \
  --network host \
  -e DAEMON_HOSTNAME=$(hostname) \
  -e DAEMON_PORT='3030' \
  -e DAEMON_PG_URI=jdbc:postgresql://$(hostname):5432/test \
  -e DAEMON_PG_USER='test' \
  -e DAEMON_WSK_API_HOST=$(hostname):3233 \
  -e DAEMON_WSK_AUTH_TOKEN='23bc46b1-71f6-4ed5-8c54-816aa4f8c502:123zO3xZCLrMN6v2BKK1dXYFpXlPkccOFqm12CdAsMgRU4VrNZ9lyGVCGuMDGIwP' \
  -e DAEMON_PG_PASSWORD='test' \
  -e DAEMON_WSK_API_VERSION=1 \
  pragmad:latest

