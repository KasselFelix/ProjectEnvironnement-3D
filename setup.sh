#!/usr/bin/env bash
# Vérifie que JOGL est bien présent dans le repo.
#
# Note historique :
# Ce projet a été développé contre JOGL 2.0-b66 (2012), qui contient encore le
# namespace `javax.media.opengl` utilisé partout dans src/. Les versions JOGL
# 2.3.2+ disponibles sur Maven Central ont retiré ce namespace, et notre code
# ne compile plus contre elles sans migration. Une tentative de migration vers
# `com.jogamp.opengl` (JOGL 2.3.2) a déclenché un crash natif dans libGLX_mesa
# sur WSLg. Le compromis retenu est de versionner JOGL b66 directement dans
# le repo (8 jars, ~50 Mo) sous JOGL/jar/.
#
# Usage : ./setup.sh
set -euo pipefail

cd "$(dirname "$0")"

REQUIRED=(
    "JOGL/jar/gluegen-rt.jar"
    "JOGL/jar/jogl-all.jar"
    "JOGL/jar/gluegen-rt-natives-linux-amd64.jar"
    "JOGL/jar/jogl-all-natives-linux-amd64.jar"
)

missing=0
for f in "${REQUIRED[@]}"; do
    if [ ! -s "$f" ]; then
        echo "✗ Manquant ou vide : $f" >&2
        missing=1
    fi
done

if [ "$missing" -ne 0 ]; then
    cat >&2 <<'EOF'

Erreur : JOGL n'est pas correctement installé dans JOGL/jar/.

Ce projet embarque JOGL 2.0-b66 directement dans le repo (les jars
doivent être présents après `git clone`). Si tu vois ce message, soit :

  - les jars n'ont pas été clonés (vérifie ton accès au repo)
  - tu as supprimé le dossier JOGL/ par erreur

Pour récupérer JOGL : reclone le repo, ou demande à un mainteneur
la version compatible.

EOF
    exit 1
fi

if ! command -v javac >/dev/null; then
    echo "Erreur : javac introuvable." >&2
    echo "Installe un JDK : sudo apt install -y openjdk-17-jdk" >&2
    exit 1
fi

echo "JOGL OK ($(ls JOGL/jar/*.jar | wc -l) jars présents)."
echo "JDK OK ($(javac -version 2>&1))."
echo
echo "Tu peux lancer : ./run.sh"
