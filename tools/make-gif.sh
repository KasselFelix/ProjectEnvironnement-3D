#!/usr/bin/env bash
# Assemble les images du mode -Dautoshot=gif en GIF de présentation (README).
#
# 1. Produire les images (screenshots/gif/frame-NNNN.png), depuis ProjectEnvironnement-3D/ :
#      Linux / WSL : ./build.sh && java -Dautoshot=gif -cp "bin:JOGL/jar/*" app.MyEcosystem
#      Windows     : java -Dautoshot=gif -cp "bin;JOGL\jar\*" app.MyEcosystem   (après compilation)
#    Le jeu déroule seul le scénario (vue aérienne, gros plan, éruption, nuit)
#    puis se ferme. Le rendu se fait image par image : le résultat ne dépend
#    pas du FPS de la machine.
# 2. Assembler (nécessite ffmpeg) :
#      tools/make-gif.sh                  # → docs/presentation.gif
#      WIDTH=640 FPS=10 tools/make-gif.sh # largeur et cadence réglables
set -euo pipefail

cd "$(dirname "$0")/.."

WIDTH="${WIDTH:-720}"
FPS="${FPS:-10}"
COLORS="${COLORS:-128}"
IN="screenshots/gif/frame-%04d.png"
OUT="${OUT:-docs/presentation.gif}"

if ! command -v ffmpeg >/dev/null; then
    echo "ffmpeg introuvable (sudo apt install ffmpeg)." >&2
    exit 1
fi
if [ ! -f screenshots/gif/frame-0000.png ]; then
    echo "Aucune image dans screenshots/gif/ : lancer d'abord le jeu avec -Dautoshot=gif." >&2
    exit 1
fi

# Palette calculée sur les zones qui bougent (stats_mode=diff) et tramage léger :
# meilleur rapport qualité / poids pour un GIF de rendu 3D.
ffmpeg -v error -y -framerate "$FPS" -i "$IN" \
    -vf "scale=${WIDTH}:-2:flags=lanczos,split[a][b];[a]palettegen=max_colors=${COLORS}:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle" \
    -loop 0 "$OUT"

echo "GIF : $OUT ($(du -h "$OUT" | cut -f1))"
