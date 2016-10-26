#!/bin/sh

if [[ -z "$1" ]]; then
    echo usage: $0 war-or-ear
    exit 1
fi

mvn clean package
java -jar target/findclassduplicates-maven-plugin-1.0.0-SNAPSHOT.jar $1
