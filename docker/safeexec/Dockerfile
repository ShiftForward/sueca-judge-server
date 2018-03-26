FROM openjdk:8-jdk-alpine

RUN apk update && apk upgrade
RUN apk add --no-cache bash git openssh
RUN apk add --no-cache make cmake

RUN apk add --no-cache gcc g++ musl-dev
RUN apk add --no-cache python3 python3-dev
RUN apk add --no-cache nodejs nodejs-npm

RUN /usr/bin/easy_install* pip
RUN pip install --upgrade pip
RUN pip install numpy

RUN npm i --save lodash

RUN git clone https://github.com/ochko/safeexec.git
ENV CXX "gcc"
RUN cd safeexec && cmake . && make && make install
ADD run.sh /run.sh
RUN chmod +x run.sh

# protect all world-writable folders
RUN find / -type d \( -perm -o+w \) -exec chmod o-w {} \;

ENTRYPOINT ["/bin/bash"]
