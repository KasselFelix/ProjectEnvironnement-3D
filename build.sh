#!/usr/bin/env bash
# Compile tout le code Java de src/ vers bin/
# Usage : ./build.sh
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -d JOGL/jar ] || [ -z "$(ls -A JOGL/jar 2>/dev/null)" ]; then
    echo "Erreur : JOGL absent. Lance d'abord : ./setup.sh" >&2
    exit 1
fi

if ! command -v javac >/dev/null; then
    echo "Erreur : javac introuvable. Installe un JDK : sudo apt install -y openjdk-17-jdk" >&2
    exit 1
fi

echo "Nettoyage de bin/…"
rm -rf bin
mkdir bin

echo "Compilation…"
# Note : on quote "JOGL/jar/*" pour que javac fasse l'expansion lui-même (pas le shell).
javac -d bin -cp "JOGL/jar/*" $(find src -name '*.java')

echo "Compilation terminée → bin/"
