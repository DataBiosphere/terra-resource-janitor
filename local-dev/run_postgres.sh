#!/usr/bin/env bash

POSTGRES_VERSION=12.3
start() {


    echo "attempting to remove old $CONTAINER container..."
    docker rm -f $CONTAINER || echo "docker rm failed. nothing to rm."

    # start up postgres
    echo "starting up postgres container..."
    docker run --name $CONTAINER -e POSTGRES_USER=janitor-test -e POSTGRES_PASSWORD=janitor-test -e POSTGRES_DB=testdb -d -p "$POSTGRES_PORT:5432" postgres:$POSTGRES_VERSION

    # validate postgres
    echo "running postgres validation..."
    docker run --rm --link $CONTAINER:postgres -v $PWD/local-dev/sql_validate.sh:/working/sql_validate.sh postgres:$POSTGRES_VERSION /working/sql_validate.sh janitor
    if [ 0 -eq $? ]; then
        echo "postgres validation succeeded."
    else
        echo "postgres validation failed."
        exit 1
    fi

}

stop() {
    echo "Stopping docker $CONTAINER container..."
    docker stop $CONTAINER || echo "postgres stop failed. container already stopped."
    docker rm -v $CONTAINER || echo "postgres rm -v failed.  container already destroyed."
}

CONTAINER=postgres
COMMAND=$1
POSTGRES_PORT=${2:-"5432"}

if [ ${#@} == 0 ]; then
    echo "Usage: $0 stop|start"
    exit 1
fi

if [ $COMMAND = "start" ]; then
    start
elif [ $COMMAND = "stop" ]; then
    stop
else
    exit 1
fi
