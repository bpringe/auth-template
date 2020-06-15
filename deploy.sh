#!/bin/bash

IMAGE_NAME="<registry>/<repo>"
PROD_CONFIG_DIRECTORY="/usr/auth-template"

docker build -t $IMAGE_NAME:$(git rev-parse HEAD) -t $IMAGE_NAME:latest .
docker login
docker push $IMAGE_NAME:$(git rev-parse HEAD)
docker push $IMAGE_NAME:latest

# --net=host is used in `docker run` to make it easier to connect to postgres using localhost
# If need to use multiple containers for web server on same machine, will need another solution
ssh -t <user>@<your-server-ip> << EOF
docker login
docker pull $IMAGE_NAME:latest
docker stop auth-template
docker rm auth-template
docker run -d --net host --restart always --name auth-template --mount type=bind,source=$PROD_CONFIG_DIRECTORY,destination=/config,readonly $IMAGE_NAME:latest
docker ps
echo $(date) 'Waiting for server to start...'
docker logs -f auth-template
EOF
