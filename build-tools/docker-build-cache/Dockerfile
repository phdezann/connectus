FROM ubuntu:16.04

ARG USER_UID=1000
ARG USER_GID=1000

RUN groupadd -r --gid $USER_GID connectus                && \
    useradd -r --uid $USER_UID --gid $USER_GID connectus && \
    mkdir /data                                          && \
    chown $USER_GID:$USER_UID /data

VOLUME /data

USER connectus
