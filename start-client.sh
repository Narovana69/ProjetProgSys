#!/bin/bash
cd "$(dirname "$0")"

echo "🚀 Starting NEXO Client..."

mvn compile -q || exit 1

JAVAFX_VERSION=17.0.2
M2_REPO="$HOME/.m2/repository/org/openjfx"

MODULE_PATH=""
for module in javafx-base javafx-controls javafx-graphics javafx-fxml; do
    MODULE_PATH="$MODULE_PATH:$M2_REPO/$module/$JAVAFX_VERSION/$module-$JAVAFX_VERSION.jar"
    if [ -f "$M2_REPO/$module/$JAVAFX_VERSION/$module-$JAVAFX_VERSION-linux.jar" ]; then
        MODULE_PATH="$MODULE_PATH:$M2_REPO/$module/$JAVAFX_VERSION/$module-$JAVAFX_VERSION-linux.jar"
    fi
done
MODULE_PATH="${MODULE_PATH:1}"

# Build classpath with all Maven dependencies (includes OpenCV)
OPENCV_JAR="$HOME/.m2/repository/org/openpnp/opencv/4.7.0-0/opencv-4.7.0-0.jar"
EXTRA_CP=""
if [ -f "$OPENCV_JAR" ]; then
    EXTRA_CP=":$OPENCV_JAR"
fi

java \
    -Xms128m \
    -Xmx512m \
    --module-path "$MODULE_PATH" \
    --add-modules javafx.controls,javafx.fxml \
    -cp "target/classes${EXTRA_CP}" \
    com.reseau.client.ClientApp
