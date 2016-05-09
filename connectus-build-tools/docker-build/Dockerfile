FROM ubuntu:16.04

ARG USER_UID=1000
ARG USER_GID=1000

ADD bin/accept-apt-repository /usr/local/bin/
ADD bin/accept-oracle-licence /usr/local/bin/

RUN apt-get update -y -q                                                                                 \
    && apt-get install -y -q wget curl expect git-core net-tools socat dosfstools iputils-ping netcat jq \
    && apt-get install -y -q software-properties-common                                                  \
    && accept-apt-repository add-apt-repository ppa:webupd8team/java && apt-get update -y -q             \
    && accept-oracle-licence apt-get install -y -q oracle-java7-installer oracle-java8-installer         \
    && apt-get install -y -q gcc-multilib lib32ncurses5 lib32z1 mesa-utils pciutils                      \
    && apt-get clean                                                                                     \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA8_HOME /usr/lib/jvm/java-8-oracle
ENV JAVA7_HOME /usr/lib/jvm/java-7-oracle
ENV JAVA_HOME ${JAVA8_HOME}

RUN groupadd --gid $USER_GID connectus \
    && useradd -m --uid $USER_UID --gid $USER_GID connectus

RUN chown connectus:connectus /opt
USER connectus
WORKDIR /opt

ENV ANDROID_HOME /data/android-sdk-linux
ENV ANDROID_SDK_HOME ${ANDROID_HOME}
ENV PATH ${PATH}:${ANDROID_SDK_HOME}/tools:${ANDROID_SDK_HOME}/platform-tools
RUN ln -s ${ANDROID_SDK_HOME} /opt/android-sdk-linux

RUN wget -q https://dl.bintray.com/sbt/native-packages/sbt/0.13.9/sbt-0.13.9.tgz
RUN tar xzf sbt-0.13.9.tgz
RUN rm -f sbt-0.13.9.tgz

ENV SBT_HOME /opt/sbt
ENV PATH ${PATH}:${SBT_HOME}/bin

ENV GRADLE_USER_HOME=/data/gradle
RUN echo "-sbt-boot /data/sbt/boot" >> /opt/sbt/conf/sbtopts
RUN echo "-ivy      /data/sbt/ivy2" >> /opt/sbt/conf/sbtopts
ADD conf/m2/settings.xml /home/connectus/.m2/

ADD bin/accept-all-licence /usr/local/bin/
ADD bin/init-android-sdk /usr/local/bin/
ADD bin/init-emulator /usr/local/bin/
ADD bin/debug-connected-android-test /usr/local/bin/
ADD bin/deploy-to-heroku /usr/local/bin/
ADD bin/upload-release-description /usr/local/bin/
ADD bin/upload-apk-to-dropbox /usr/local/bin/

ADD conf/android-sdk /home/connectus/android-sdk-conf/
