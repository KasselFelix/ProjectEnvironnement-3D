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

# Séparateur de classpath : « ; » pour le java Windows (Git Bash), « : » ailleurs.
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
    *)                    SEP=':' ;;
esac

# 3. Compilation des tests.
mkdir -p test/bin
echo "Compilation des tests…"
javac -d test/bin \
    -cp "bin${SEP}JOGL/jar/*${SEP}test/lib/*" \
    $(find test/src -name '*.java')

# 4. Exécution.
# Le lanceur JUnit n'étend pas le joker « * » : on liste les jars JOGL un par un.
RUN_CP="bin${SEP}test/bin"
for jar in JOGL/jar/*.jar; do
    RUN_CP="${RUN_CP}${SEP}${jar}"
done

# --enable-native-access : supprime l'avertissement « restricted method » des JDK 24+.
echo "Exécution des tests…"
java --enable-native-access=ALL-UNNAMED \
    -jar test/lib/junit-platform-console-standalone-*.jar execute \
    --class-path "$RUN_CP" \
    --scan-class-path
