FROM ubuntu:16.04

ARG USER_UID=1000
ARG USER_GID=1000

RUN apt-get update -y -q                                                                              \
    && apt-get install -y -q wget curl expect git-core net-tools socat dosfstools iputils-ping netcat \
    && apt-get install -y -q openjdk-7-jdk openjdk-7-source openjdk-8-jdk openjdk-8-source            \
    && apt-get install -y -q gcc-multilib lib32ncurses5 lib32z1                                       \
    && apt-get clean                                                                                  \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA8_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV JAVA7_HOME /usr/lib/jvm/java-7-openjdk-amd64
ENV JAVA_HOME ${JAVA8_HOME}

RUN groupadd --gid $USER_GID connectus
RUN useradd -m --uid $USER_UID --gid $USER_GID connectus

RUN chown $USER_GID:$USER_UID /opt
USER connectus
WORKDIR /opt

RUN wget -q https://dl.google.com/android/android-sdk_r24.4.1-linux.tgz
RUN tar xzf android-sdk_r24.4.1-linux.tgz
RUN rm -f android-sdk_r24.4.1-linux.tgz

ENV ANDROID_HOME /opt/android-sdk-linux
ENV ANDROID_SDK_HOME ${ANDROID_HOME}
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools

ADD accept-all-licence /usr/local/bin/
RUN accept-all-licence                                  \
    android update sdk --all --no-ui --force --filter   \
    \"                                                  \
    build-tools-23.0.2,                                 \
    android-19,                                         \
    sample-19,                                          \
    sys-img-armeabi-v7a-android-19,                     \
    addon-google_apis-google-19,                        \
    android-23,                                         \
    doc-23,                                             \
    sample-23,                                          \
    platform-tools,                                     \
    extra-android-m2repository,                         \
    extra-google-m2repository,                          \
    extra-android-support,                              \
    extra-google-auto,                                  \
    extra-google-google_play_services,                  \
    extra-google-play_apk_expansion,                    \
    extra-google-play_billing,                          \
    extra-google-play_licensing,                        \
    extra-google-simulators,                            \
    extra-google-webdriver                              \
    \"

RUN wget -q https://dl.bintray.com/sbt/native-packages/sbt/0.13.9/sbt-0.13.9.tgz
RUN tar xzf sbt-0.13.9.tgz
RUN rm -f sbt-0.13.9.tgz

ENV SBT_HOME /opt/sbt
ENV PATH ${PATH}:${SBT_HOME}/bin

ENV GRADLE_USER_HOME=/data/gradle
RUN echo "-sbt-boot /data/sbt/boot" >> /opt/sbt/conf/sbtopts
RUN echo "-ivy      /data/sbt/ivy2" >> /opt/sbt/conf/sbtopts

ADD init-emulator /usr/local/bin/
ADD deploy-to-heroku /usr/local/bin/
ADD config.ini /home/connectus
