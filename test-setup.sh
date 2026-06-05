#!/usr/bin/env bash
# Télécharge JUnit 5 dans test/lib/. Deux ensembles de jars :
#  1. Le fat-jar `junit-platform-console-standalone` utilisé par ./test.sh
#     (CLI auto-suffisante, un seul jar à connaître).
#  2. Les jars individuels (jupiter-api/engine, platform-commons/engine/
#     launcher, opentest4j, apiguardian-api) utilisés par le Test Runner
#     for Java de VS Code — qui n'arrive pas à découvrir les classes JUnit
#     dans le fat jar shaded.
# Idempotent : chaque jar manquant est téléchargé, les présents sont gardés.
# Usage : ./test-setup.sh
set -euo pipefail

cd "$(dirname "$0")"

mkdir -p test/lib

# Versions pinned. Garder cohérentes :
#  - junit-platform-*       : 1.10.2
#  - junit-jupiter-*        : 5.10.2
#  - opentest4j             : 1.3.0  (dépendance de jupiter-api)
#  - apiguardian-api        : 1.1.2  (tags @API utilisés par l'API publique)
PLATFORM_VERSION=1.10.2
JUPITER_VERSION=5.10.2
OPENTEST4J_VERSION=1.3.0
APIGUARDIAN_VERSION=1.1.2

# Liste des jars à télécharger : "URL → chemin local"
declare -a JARS=(
    "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/${PLATFORM_VERSION}/junit-platform-console-standalone-${PLATFORM_VERSION}.jar|test/lib/junit-platform-console-standalone-${PLATFORM_VERSION}.jar"
    "https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/${JUPITER_VERSION}/junit-jupiter-api-${JUPITER_VERSION}.jar|test/lib/junit-jupiter-api-${JUPITER_VERSION}.jar"
    "https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-engine/${JUPITER_VERSION}/junit-jupiter-engine-${JUPITER_VERSION}.jar|test/lib/junit-jupiter-engine-${JUPITER_VERSION}.jar"
    "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-commons/${PLATFORM_VERSION}/junit-platform-commons-${PLATFORM_VERSION}.jar|test/lib/junit-platform-commons-${PLATFORM_VERSION}.jar"
    "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-engine/${PLATFORM_VERSION}/junit-platform-engine-${PLATFORM_VERSION}.jar|test/lib/junit-platform-engine-${PLATFORM_VERSION}.jar"
    "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-launcher/${PLATFORM_VERSION}/junit-platform-launcher-${PLATFORM_VERSION}.jar|test/lib/junit-platform-launcher-${PLATFORM_VERSION}.jar"
    "https://repo1.maven.org/maven2/org/opentest4j/opentest4j/${OPENTEST4J_VERSION}/opentest4j-${OPENTEST4J_VERSION}.jar|test/lib/opentest4j-${OPENTEST4J_VERSION}.jar"
    "https://repo1.maven.org/maven2/org/apiguardian/apiguardian-api/${APIGUARDIAN_VERSION}/apiguardian-api-${APIGUARDIAN_VERSION}.jar|test/lib/apiguardian-api-${APIGUARDIAN_VERSION}.jar"
)

if ! command -v curl >/dev/null; then
    echo "Erreur : curl introuvable. Installe-le ou télécharge manuellement les jars listés dans ce script." >&2
    exit 1
fi

downloaded=0
skipped=0
for entry in "${JARS[@]}"; do
    url="${entry%%|*}"
    dest="${entry##*|}"
    if [ -f "$dest" ]; then
        skipped=$((skipped + 1))
        continue
    fi
    echo "Téléchargement : $(basename "$dest")"
    curl -fsSL -o "$dest" "$url"
    downloaded=$((downloaded + 1))
done

echo "Setup terminé : $downloaded jar(s) téléchargé(s), $skipped déjà présent(s)."
