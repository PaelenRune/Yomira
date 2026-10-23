#!/bin/sh

# Resolve the project directory even when invoked through a symbolic link.
app_path=$0
while [ -h "$app_path" ]; do
    app_dir=$(cd -P "$(dirname "$app_path")" && pwd) || exit 1
    link=$(readlink "$app_path") || exit 1
    case "$link" in
        /*) app_path=$link ;;
        *) app_path=$app_dir/$link ;;
    esac
done
APP_HOME=$(cd -P "$(dirname "$app_path")" && pwd) || exit 1

if [ -n "$JAVA_HOME" ]; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "Java not found. Set JAVA_HOME to a JDK 17 installation." >&2
    exit 1
fi

# JAVA_OPTS and GRADLE_OPTS follow the conventional wrapper's shell option syntax.
exec "$JAVACMD" -Xmx64m -Xms64m ${JAVA_OPTS:-} ${GRADLE_OPTS:-} \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
