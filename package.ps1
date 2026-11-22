<#
.SYNOPSIS
  Construit l'exécutable Windows du jeu et son archive .zip, prête à joindre à une Release GitHub.

.DESCRIPTION
  1. Compile src/ vers build/classes (Java 17).
  2. Prépare build/package : le jar du jeu, les jars JOGL et les DLL natives
     extraites des jars *-natives-windows-amd64 du dossier JOGL/jar.
  3. Lance jpackage, qui produit build/dist/ProjectEnvironnement-3D avec un runtime Java embarqué.
  4. Copie models/, textures/ et landscapes/ à côté de ProjectEnvironnement-3D.exe.
  5. Zippe le tout dans build/ProjectEnvironnement-3D-<version>-windows-x64.zip.

  Prérequis : un JDK 17 ou plus récent (avec jpackage), trouvé via -JdkHome, JAVA_HOME ou le PATH.

.EXAMPLE
  .\package.ps1

.EXAMPLE
  .\package.ps1 -Version 1.1.0 -JdkHome "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
#>
param(
    [string]$Version = '1.0.0',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$name = 'ProjectEnvironnement-3D'

function Get-JdkTool([string]$tool) {
    if ($JdkHome -and (Test-Path (Join-Path $JdkHome "bin\$tool.exe"))) { return (Join-Path $JdkHome "bin\$tool.exe") }
    $cmd = Get-Command $tool -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "$tool introuvable : installe un JDK 17+ (jpackage inclus) ou passe -JdkHome <dossier du JDK>."
}
$javac    = Get-JdkTool 'javac'
$jar      = Get-JdkTool 'jar'
$jpackage = Get-JdkTool 'jpackage'

$build   = Join-Path $PSScriptRoot 'build'
$classes = Join-Path $build 'classes'
$pkg     = Join-Path $build 'package'
$dist    = Join-Path $build 'dist'
$zip     = Join-Path $build "$name-$Version-windows-x64.zip"

Write-Host 'Nettoyage de build\ ...'
foreach ($d in $classes, $pkg, $dist) { if (Test-Path $d) { Remove-Item $d -Recurse -Force } }
if (Test-Path $zip) { Remove-Item $zip -Force }
New-Item -ItemType Directory -Force $classes, (Join-Path $pkg 'natives') | Out-Null

Write-Host 'Compilation (Java 17) ...'
$sources = Join-Path $build 'sources.txt'
Get-ChildItem src -Recurse -Filter *.java |
    ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' } |
    Set-Content $sources -Encoding ASCII
& $javac --release 17 -encoding UTF-8 -d $classes -cp 'JOGL\jar\*' "@$sources"
if ($LASTEXITCODE) { throw 'Échec de la compilation.' }

Write-Host 'Préparation de build\package ...'
& $jar --create --file (Join-Path $pkg "$name.jar") --main-class app.MyEcosystem -C $classes .
if ($LASTEXITCODE) { throw 'Échec de la création du jar.' }
$joglJars = 'gluegen-rt.jar', 'jogl-all.jar', 'gluegen-rt-natives-windows-amd64.jar', 'jogl-all-natives-windows-amd64.jar'
foreach ($j in $joglJars) { Copy-Item (Join-Path 'JOGL\jar' $j) $pkg }

# DLL natives : java.library.path pointe vers app\natives dans l'exécutable
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($j in $joglJars | Where-Object { $_ -like '*natives-windows-amd64.jar' }) {
    $z = [IO.Compression.ZipFile]::OpenRead((Resolve-Path (Join-Path 'JOGL\jar' $j)))
    try {
        foreach ($e in $z.Entries | Where-Object Name -like '*.dll') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $pkg "natives\$($e.Name)"), $true)
        }
    } finally { $z.Dispose() }
}

Write-Host 'jpackage (runtime Java embarqué) ...'
& $jpackage --type app-image --name $name --app-version $Version `
    --input $pkg --main-jar "$name.jar" --main-class app.MyEcosystem `
    --dest $dist --icon 'assets\icon.ico' `
    --java-options '--enable-native-access=ALL-UNNAMED' `
    --java-options '-Djava.library.path=$APPDIR\natives' `
    --java-options '-Duser.dir=$APPDIR'
if ($LASTEXITCODE) { throw 'Échec de jpackage.' }

# Le jeu lit ses ressources en chemin relatif, depuis le dossier de l'exécutable
$app = Join-Path $dist $name
Copy-Item models, textures, landscapes -Destination $app -Recurse

Write-Host "Archive $zip ..."
[IO.Compression.ZipFile]::CreateFromDirectory($app, $zip, [IO.Compression.CompressionLevel]::Optimal, $true)

$mo = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host ''
Write-Host "Exécutable : $app\$name.exe"
Write-Host "Archive    : $zip ($mo Mo)"
