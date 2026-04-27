#!/bin/bash
# UAE4ARM 2026 - Build, Install and Run script

# Ensure we are in the root directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

echo "Building and installing UAE4ARM 2026..."
cd android && ./gradlew installDebug
if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo "Launching MainActivity..."
adb shell am start -n com.uae4arm2026.debug/com.uae4arm2026.ui.MainActivity
