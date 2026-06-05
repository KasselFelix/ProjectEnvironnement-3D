#!/usr/bin/env bash
# Compile les tests unitaires et les exécute via JUnit Platform Console.
# Pré-requis : ./build.sh aura été appelé (ou le sera automatiquement ici).
# Usage : ./test.sh
set -euo pipefail

cd "$(dirname "$0")"

# 1. Build du code de production si nécessaire.
if [ ! -d bin ] || [ -z "$(ls -A bin 2>/dev/null)" ]; then
    echo "bin/ vide — appel de ./build.sh…"
    ./build.sh
fi

# 2. JUnit téléchargé si absent.
./test-setup.sh

# 3. Compilation des tests.
mkdir -p test/bin
echo "Compilation des tests…"
javac -d test/bin \
    -cp "bin:JOGL/jar/*:test/lib/*" \
    $(find test/src -name '*.java')

# 4. Exécution.
echo "Exécution des tests…"
java -jar test/lib/junit-platform-console-standalone-*.jar \
    --class-path "bin:test/bin:JOGL/jar/*" \
    --scan-class-path
