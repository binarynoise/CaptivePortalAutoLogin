#!/bin/sh
set -e
set -x

cd -- "$(dirname -- "$0")"
cd ..

git pull

systemctl enable \
    "$PWD"/deployment/api-server.service \
    "$PWD"/deployment/portal-proxy.service

./gradlew \
    :api:server:shadowJar \
    :portalProxy:shadowJar

mv api/server/build/libs/server-shadow.jar deployment/api-server-shadow.jar
mv portalProxy/build/libs/portalProxy-shadow.jar deployment/portal-proxy-shadow.jar

ln -sfv "$PWD"/deployment/nginx.conf /etc/nginx/conf.d/captiveportalautologin.conf
nginx -t

systemctl daemon-reload

systemctl reload-or-restart \
    nginx.service

systemctl restart \
    api-server.service \
    portal-proxy.service
