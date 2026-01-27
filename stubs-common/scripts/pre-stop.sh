#!/usr/bin/env sh

xargs -rt -a /itf/application.pid kill -SIGTERM
sleep 59