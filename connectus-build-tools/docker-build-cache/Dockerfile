FROM ubuntu:16.04

ARG USER_UID=1000
ARG USER_GID=1000

RUN groupadd --gid $USER_GID connectus \
    && useradd -m --uid $USER_UID --gid $USER_GID connectus

RUN mkdir /data \
    && chown connectus:connectus /data
VOLUME /data

USER connectus
