FROM openjdk:8-slim-buster

ENV CLOJURE_VERSION=1.10.1.502

WORKDIR /tmp

RUN \
apt-get update && \
apt-get install -y curl wget && \
rm -rf /var/lib/apt/lists/* && \
wget https://download.clojure.org/install/linux-install-$CLOJURE_VERSION.sh && \
chmod +x linux-install-$CLOJURE_VERSION.sh && \
./linux-install-$CLOJURE_VERSION.sh && \
clojure -e "(clojure-version)" && \
curl -sL https://deb.nodesource.com/setup_14.x | bash - && \
apt-get install -y nodejs

RUN mkdir -p /app
RUN mkdir -p /config

WORKDIR /app

COPY deps.edn .
RUN clojure -e nil

COPY package.json .
RUN npm install

COPY . .

RUN npx shadow-cljs release app

RUN apt-get remove -y --purge curl wget nodejs && \
apt-get -qy autoremove

EXPOSE 8080

CMD ["clojure", "-A:prod", "-m", "auth-template.server"]