#!/bin/sh
exec "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" "$@" 2>/dev/null || \
JAVA_EXE='java'; exec "$JAVA_EXE" -classpath "gradle/wrapper/gradle-wrapper.jar" \
org.gradle.wrapper.GradleWrapperMain "$@"
