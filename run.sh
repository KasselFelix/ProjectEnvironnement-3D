#!/usr/bin/env bash
# Compile puis lance la simulation.
# Usage : ./run.sh
set -euo pipefail

cd "$(dirname "$0")"

./build.sh

# --enable-native-access : supprime l'avertissement « restricted method » des
# JDK 24+ (GlueGen charge ses natives par System.load). Option reconnue dès le JDK 17.
JAVA_OPTS=(--enable-native-access=ALL-UNNAMED)

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
        # Windows (Git Bash) : le java Windows sépare le classpath par « ; ».
        # Pas de Mesa ici : le pilote natif du GPU sert directement.
        echo "Lancement de MyEcosystem… (Windows)"
        java "${JAVA_OPTS[@]}" -cp "bin;JOGL/jar/*" app.MyEcosystem
        ;;
    *)
        # Tentative GPU : route Mesa via le driver d3d12 vers WSLg/DXGI → GPU NVIDIA.
        # Sans ces vars, Mesa retombe sur llvmpipe (software, ~7 fps observé).
        # Avec, on devrait passer à 100+ fps. Si Java SEGV au lancement (CLAUDE.md
        # mentionne ce risque avec certaines versions JOGL), commenter ces 3 lignes
        # et remettre LIBGL_ALWAYS_SOFTWARE=1.
        export LIBGL_ALWAYS_SOFTWARE=0
        export MESA_LOADER_DRIVER_OVERRIDE=d3d12
        export GALLIUM_DRIVER=d3d12

        echo "Lancement de MyEcosystem… (GPU via d3d12)"
        java "${JAVA_OPTS[@]}" -cp "bin:JOGL/jar/*" app.MyEcosystem
        ;;
esac
