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

java \
    -Xms128m \
    -Xmx512m \
    --module-path "$MODULE_PATH" \
    --add-modules javafx.controls,javafx.fxml \
    -cp "target/classes" \
    com.reseau.client.ClientApp
