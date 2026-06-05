# Projet Environnement 3D

Création et modélisation d'un écosystème simplifié dynamique 3D 
Implémentant plusieurs mécanisme : automate cellulaire, 
arbre de comportement, générateur d’altitude, modèle proie prédateur, 
Perlin Noise, Simplex Noise, Systeme de Lindemayer, monde torique.

![Capture d'écran de la simulation](docs/screenshot.png)

Voir [docs/presentation-projet.pdf](docs/presentation-projet.pdf) pour la présentation du projet académique.

## Caractéristiques

- 🌍 **Monde torique** 128×128 cellules — les bords se rejoignent, la distance se calcule modulo
- 🏔️ **Génération de terrain** par bruit de Perlin (`PerlinNoiseLandscapeGenerator`) ou chargement d'images PNG (`landscapes/`)
- 🌲 **Cinq automates cellulaires** : forêt, herbe, lave, pierre, niveau du sol — chacun avec ses propres règles
- 🐺🐑👤 **Système multi-agents** : prédateurs (loups), proies (moutons), humains
- 🌅 **Cycle jour / nuit** (2000 itérations par cycle complet)
- 🌳 **Arbre fractal** généré par L-système
- 🌋 **Éruption volcanique** déclenchable au clavier
- 🧱 Modèles 3D OBJ chargés depuis `models/`

## Prérequis

| | Version recommandée | Comment l'obtenir |
|---|---|---|
| **JDK** | OpenJDK 17 (11+ accepté) | `sudo apt install openjdk-17-jdk` |
| **Affichage OpenGL** | n'importe quel X11/Wayland | natif sur Linux ; WSLg sur Windows 11 ; XQuartz sur macOS |
| **JOGL 2.0-b66** | versionné dans le repo | déjà présent dans `JOGL/` |

JOGL est embarqué dans le repo (~50 Mo) parce que les versions modernes (2.3.2+) ont retiré le namespace `javax.media.opengl` utilisé par ce code. Voir [docs/CHANGELOG.md](docs/CHANGELOG.md) et `setup.sh` pour les détails.

## Installation et lancement

Trois manières d'utiliser ce projet, au choix : ligne de commande (la plus simple, la plus portable), Eclipse, ou VS Code. Les trois fonctionnent sur Linux, macOS et Windows — les particularités par plateforme (display OpenGL, shell, séparateur de classpath) sont décrites plus bas, voir [Notes par plateforme](#notes-pour-linux-et-macos).

### Option 1 — Ligne de commande (recommandé)

```bash
git clone https://github.com/KasselFelix/ProjectEnvironnement-3D.git
cd ProjectEnvironnement-3D
./setup.sh    # vérifie que JOGL et le JDK sont prêts
./run.sh      # compile (au besoin) puis lance la simulation
```

Une fenêtre OpenGL s'ouvre avec le paysage 3D. Pour lancer les tests JUnit :

```bash
./test-setup.sh   # une seule fois — télécharge le jar JUnit dans test/lib/
./test.sh         # compile + exécute tous les tests
```

### Option 2 — Eclipse

Le projet contient `.classpath` et `.project` versionnés dans le repo, donc l'import est immédiat.

1. **Prérequis** : avoir un **JDK 17** installé dans Eclipse (`Window → Preferences → Java → Installed JREs` → Add → pointer sur un JDK 17 si pas déjà présent).
2. **Import** : `File → Open Projects from File System…` → choisir le dossier `ProjectEnvironnement-3D/` → Finish. Le projet apparaît dans le Package Explorer sous le nom `WorldOfCells-LI260` (nom interne hérité du framework, c'est juste une étiquette).
3. **Lancer la simulation** : clic droit sur `src/applications/simpleworld/MyEcosystem.java` → **Run As → Java Application**. Eclipse crée automatiquement une Run Configuration ; les natives JOGL (Linux/Mac/Windows) sont chargées par GlueGen selon ton OS.
4. **Lancer les tests** : lancer d'abord `./test-setup.sh` une fois dans un terminal pour télécharger le jar JUnit, puis clic droit sur `test/src/applications/simpleworld/LavaCATest.java` → **Run As → JUnit Test**.

Si "Unbound classpath container" s'affiche → le JDK 17 n'est pas configuré dans Eclipse (étape 1). Si des accents cassés sont visible (`?` à la place de `é`, `à`…) → `Window → Preferences → General → Workspace → Text file encoding: UTF-8`.

### Option 3 — VS Code

Le projet contient `.vscode/settings.json`, `.vscode/tasks.json`, `.vscode/extensions.json` versionnés.

1. **Ouvrir le dossier** `ProjectEnvironnement-3D/` directement (pas son parent) — les paramètres sont relatifs à ce dossier.
2. **Installer les extensions recommandées** : VS Code propose une popup à la première ouverture. Sinon : icône Extensions → onglet "Recommended" → tout installer. Les indispensables sont **Extension Pack for Java** (Microsoft), **Test Runner for Java**, et **WSL** si tu es sous Windows.
3. **Lancer la simulation** par les tasks pré-configurées :
   - **Build** : `Ctrl+Shift+B` (default build task)
   - **Run** : `Ctrl+Shift+P` → `Tasks: Run Task` → `Run`
   - Pour lier **Run** à `F5`, ajouter ce keybinding (`Ctrl+Shift+P` → `Preferences: Open Keyboard Shortcuts (JSON)`) :
     ```json
     {
       "key": "f5",
       "command": "workbench.action.tasks.runTask",
       "args": "Run",
       "when": "resourceExtname == '.java'"
     }
     ```
4. **Lancer les tests** : lancer `./test-setup.sh` une fois dans un terminal intégré pour récupérer le jar JUnit, puis :
   - Cliquer sur l'onglet **Testing** (icône bécher dans la barre latérale) → arbre `LavaCATest`, `LoupTest` → ▶ pour run, 🐞 pour debug.
   - Ou clic droit sur une méthode `@Test` dans un fichier de test → `Run Test`.

Si l'arbre des tests reste vide après ouverture : `Ctrl+Shift+P` → **`Java: Clean Java Language Server Workspace`** → "Restart and delete". L'extension re-indexe le projet (~30 s) et reconnaît les tests.

### Notes pour Linux et macOS

Aucune configuration spécifique necessaire. Les scripts `.sh`, le classpath séparé par `:`, et OpenGL sur le serveur X / Wayland (Linux) ou Quartz (macOS) sont gérés out-of-the-box.

### Notes pour WSL (Linux subsystem dans Windows)

Ce projet a été développé et testé sous **WSL2 + WSLg** (Windows 11). Le shell est `bash` ou `zsh`, les scripts `.sh` marchent comme sur Linux natif. Une seule chose à vérifier — que WSLg est bien actif :

```bash
echo $WAYLAND_DISPLAY    # doit afficher "wayland-0"
```

Sur Windows 10 (sans WSLg), il faut un serveur X côté Windows (VcXsrv, X410, XLaunch) et exporter `DISPLAY=:0` avant `./run.sh`.

**TL;DR sur le serveur X (affichage des fenêtres) :**
- **Windows 11 + WSL2** : aucun prerequis necessaire.
- **Windows 10 + WSL** : Installe VcXsrv 15 min , exporte `DISPLAY`.

**Côté IDE** : utiliser l'extension **WSL** de VS Code (déjà recommandée par le projet) pour ouvrir le dossier *depuis WSL* — VS Code se reconnecte au filesystem Linux et tout reste cohérent. Pour Eclipse : installer la version Linux d'Eclipse **dans** WSL (`sudo apt install eclipse`), pas Eclipse Windows pointant sur `\\wsl$\Ubuntu\…` (chemins cassés, performance médiocre).

### Notes pour Windows natif (PowerShell / cmd)

Les scripts `.sh` ne tournent pas dans PowerShell ou cmd. Deux options.

**Option recommandée — Git Bash.** Installer [Git for Windows](https://git-scm.com/download/win) (gratuit, ~50 Mo) fournit `bash.exe` et l'environnement Unix minimal. Ouvrir Git Bash dans le dossier `ProjectEnvironnement-3D/` puis `./setup.sh`, `./build.sh`, `./run.sh` marchent exactement comme sous Linux. C'est la voie la plus simple.

**Option alternative — commandes manuelles en PowerShell.** Si tu refuses d'installer Git Bash, voici les équivalents PowerShell des trois scripts principaux. Attention : sur Windows le **séparateur de classpath est `;`** (pas `:` comme Linux/Mac).

```powershell
# Équivalent ./build.sh
Remove-Item -Recurse -Force bin -ErrorAction SilentlyContinue
New-Item -ItemType Directory bin | Out-Null
$src = Get-ChildItem -Path src -Recurse -Filter *.java
javac -d bin -cp "JOGL\jar\*" $src.FullName

# Équivalent ./run.sh (build déjà fait)
java -cp "bin;JOGL\jar\*" applications.simpleworld.MyEcosystem
```

Pour cmd la syntaxe diffère encore ; à éviter, choisir de preference PowerShell ou Git Bash.

**Display** : aucune configuration nécessaire, OpenGL passe par le pilote GPU natif Windows. JOGL charge automatiquement `gluegen-rt-natives-windows-amd64.jar` et `jogl-all-natives-windows-amd64.jar`.

**IDE** : Eclipse Windows et VS Code Windows fonctionnent normalement — ils utilisent leur propre toolchain Java sans dépendre des scripts `.sh`, Git Bash n'est pas nécessaire si le lancement est fait via l'IDE.

## Commandes disponibles

| Script | Action |
|---|---|
| `./setup.sh` | Vérifie que les dépendances sont OK (à lancer après un `git clone`) |
| `./build.sh` | Compile tout le code de `src/` vers `bin/` |
| `./run.sh` | Compile puis lance `applications.simpleworld.MyEcosystem` |
| `./test-setup.sh` | Télécharge le jar JUnit dans `test/lib/` (idempotent, une seule fois) |
| `./test.sh` | Compile et exécute tous les tests JUnit 5 |

## Contrôles en jeu

| Touche | Action |
|---|---|
| `h` | Afficher l'aide dans le terminal |
| `v` | Bascule vue de dessus ↔ vue 3D |
| `o` | Activer / désactiver l'affichage des objets |
| `1` / `2` | Diminuer / augmenter l'amplitude des altitudes |
| `↑ ↓ → ←` | Naviguer sur la carte (monde torique) |
| `space` / `shift` | Élever / baisser la caméra |
| `q` / `d` | Rotation horizontale |
| `r` | **Éruption volcanique** 🌋 |
| `l` / `p` | Éclairage |
| `F12` | 📸 Enregistre une capture PNG dans `screenshots/` |
| `esc` | Quitter |


## Charger un paysage depuis une image

Par défaut, le terrain est généré par bruit de Perlin. Pour charger un paysage prédéfini, modifier `src/applications/simpleworld/MyEcosystem.java` :

```java
// Par défaut (génération aléatoire) :
Landscape myLandscape = new Landscape(myWorld, 200, 200, 0.7, 0.4);

// Pour charger une image (décommenter et adapter) :
//Landscape myLandscape = new Landscape(myWorld, "landscapes/landscape_default-128.png", 0.8, 0.4);
```

Les images disponibles dans `landscapes/` :

- `landscape_default-128.png` (128×128, paysage par défaut)
- `landscape_default-200.png` (200×200, plus détaillé)
- `landscape_canyon-128.png` (canyon)
- `landscape_paris-200.png` (carte stylisée)

Image grayscale, la luminance code l'altitude.

## Structure du projet

```
ProjectEnvironnement-3D/
├── src/                            # Code source Java
│   ├── applications/simpleworld/   # MyEcosystem (entry point), agents, CA spécifiques
│   ├── worlds/                     # Classe World abstraite
│   ├── graphics/                   # Landscape (rendu OpenGL, animation, input)
│   ├── cellularautomata/           # Famille des automates cellulaires
│   ├── objects/                    # Objets affichables (Tree, Grass, Monolith…)
│   ├── landscapegenerator/         # Perlin, chargement PNG
│   └── loader/                     # OBJ / MTL loaders
├── JOGL/jar/                       # JOGL 2.0-b66 (8 jars, ~50 Mo, versionné)
├── landscapes/                     # Images PNG de paysages prédéfinis
├── models/                         # Modèles 3D OBJ (loup, etc.)
├── docs/                           # PDFs (Scrum, présentation), CHANGELOG, screenshot
├── archive/                        # Version pédagogique originale (intacte)
└── setup.sh build.sh run.sh
```

Pour comprendre **comment la heightmap est générée** (pipeline du bruit de Perlin, tileability sur le tore, paramètres à régler), voir [docs/generation-terrain.txt](docs/generation-terrain.txt).

## Crédits

- **Conception et développement complet du projet** : Felix Wycherley-Kassel, UPMC / Sorbonne Université ([@KasselFelix](https://github.com/KasselFelix)) & Gabour Smail, UPMC / Sorbonne Université. Contributions :
  - **Monde torique**
  - **Génération procédurale de terrain par bruit de Perlin**
  - **Générateur de map aléatoire**
  - **Chargement de paysages depuis images PNG**
  - **Système d'agents** (Loup, Mouton, Humain)
  - **Arbres de comportement**
  - **Automates cellulaires étendus** (forêt, herbe, lave)
  - **Système Layer**
  - **Volcanisme et éruption volcanique**
  - **Grammaire générative** (L-système, arbre fractal)
  - **Cycle jour / nuit**
  - **Couche UI overlay** (menus, HUD, graphe Lotka-Volterra)
  - **Interaction caméra & picking d'agent**
  - **Modèles 3D OBJ / MTL**
  - **Suite de tests JUnit 5**
- **Archive World Of Cells** : Nicolas Bredeche, ISIR / Sorbonne Université ([nicolas.bredeche@isir.upmc.fr](mailto:nicolas.bredeche@isir.upmc.fr))

## Licence

- **JOGL** est sous licence [BSD](https://jogamp.org/jogl/doc/) (JogAmp Community).
