# =============================================================================
#  build-win.ps1 — Gera instalador .exe da Agenda Científica para Windows
# =============================================================================
# Pré-requisitos na máquina de BUILD (não no usuário final):
#   • JDK 21+ com jpackage — https://adoptium.net  (ex: Temurin 21 LTS)
#     ⚠  Certifique-se de baixar "JDK" (não JRE) e marcar "Add to PATH"
#   • Maven 3.9+ OU usar o mvnw.cmd embutido (já está no projeto)
#   • Python 3 + Pillow (opcional — converte o ícone PNG→ICO)
#     Se não tiver: pip install Pillow
#   • WiX Toolset 3.x (opcional — só para gerar .msi em vez de .exe)
#     https://github.com/wixtoolset/wix3/releases
#
# O instalador GERADO é totalmente autocontido:
#   ✅  O usuário final NÃO precisa instalar Java, OpenJDK ou nada.
#   ✅  O JRE é embutido automaticamente pelo jpackage no .exe.
#
# Como usar:
#   1. Abra o PowerShell como Administrador (não é obrigatório, mas recomendado)
#   2. Navegue até a raiz do projeto: cd C:\...\agenda
#   3. Execute:  .\build-win.ps1
#
# Para gerar .msi em vez de .exe, passe o flag:
#   .\build-win.ps1 -Type msi
# =============================================================================

param(
    [ValidateSet("exe","msi")]
    [string]$Type = "exe"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

# ── Configurações ─────────────────────────────────────────────────────────────
$APP_NAME    = "agenda-cientifica"
$DISPLAY_NAME= "Agenda Científica"
$VERSION     = "1.0.0"
$VENDOR      = "Pessoal"
$DESCRIPTION = "Agenda Científica Pessoal — Gestão de tarefas, estudos, protocolos e projetos"
$MAIN_MODULE = "com.pessoal.agenda/com.pessoal.agenda.Launcher"
$JAVAFX_VER  = "21.0.6"
$INPUT_DIR   = Join-Path $ScriptDir "target\jpackage-input"
$DIST_DIR    = Join-Path $ScriptDir "dist"
$PKG_DIR     = Join-Path $ScriptDir "packaging"
$M2          = Join-Path $env:USERPROFILE ".m2\repository"

# ══════════════════════════════════════════════════════════════════════════════
Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════╗"
Write-Host "║    Gerador de Instalador Windows — Agenda Científica     ║"
Write-Host "╚══════════════════════════════════════════════════════════╝"
Write-Host ""

# ── Verificar JDK com jpackage ────────────────────────────────────────────────
Write-Host "━━━ Verificando pré-requisitos ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

$jpackage = Get-Command "jpackage" -ErrorAction SilentlyContinue
if (-not $jpackage) {
    Write-Host ""
    Write-Host "❌  'jpackage' não encontrado no PATH." -ForegroundColor Red
    Write-Host ""
    Write-Host "    Para corrigir, instale o JDK 21 LTS (Temurin):" -ForegroundColor Yellow
    Write-Host "    https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "    Durante a instalação, marque a opção:" -ForegroundColor Yellow
    Write-Host "    ✔  'Add to PATH' / 'Set JAVA_HOME variable'" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "    Após instalar, feche e reabra o PowerShell." -ForegroundColor Yellow
    exit 1
}

$jpackageVersion = & jpackage --version 2>&1
Write-Host "✔  jpackage: $jpackageVersion"

# Verificar jmods (necessário para gerar runtime customizado)
$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    # Tentar detectar automaticamente
    $javaExe = (Get-Command "java" -ErrorAction SilentlyContinue).Source
    if ($javaExe) {
        $javaHome = Split-Path -Parent (Split-Path -Parent $javaExe)
    }
}

if ($javaHome -and (Test-Path "$javaHome\jmods")) {
    Write-Host "✔  JAVA_HOME: $javaHome"
    Write-Host "✔  jmods encontrado: $javaHome\jmods"
} else {
    Write-Host ""
    Write-Host "❌  Diretório 'jmods' não encontrado." -ForegroundColor Red
    Write-Host "    Certifique-se de ter instalado o JDK (não JRE)." -ForegroundColor Yellow
    Write-Host "    JAVA_HOME detectado: $javaHome" -ForegroundColor Yellow
    exit 1
}

# Verificar Java versão mínima 21
$javaVersion = & java -version 2>&1 | Select-String "version"
Write-Host "✔  Java: $javaVersion"

# Verificar Maven
$mvnCmd = $null
if (Test-Path "$ScriptDir\mvnw.cmd") {
    $mvnCmd = "$ScriptDir\mvnw.cmd"
    Write-Host "✔  Maven Wrapper: mvnw.cmd"
} elseif (Get-Command "mvn" -ErrorAction SilentlyContinue) {
    $mvnCmd = "mvn"
    Write-Host "✔  Maven: $(& mvn --version 2>&1 | Select-Object -First 1)"
} else {
    Write-Host ""
    Write-Host "❌  Maven não encontrado." -ForegroundColor Red
    Write-Host "    O arquivo mvnw.cmd deveria estar na raiz do projeto." -ForegroundColor Yellow
    Write-Host "    Alternativamente, instale o Maven: https://maven.apache.org/download.cgi" -ForegroundColor Cyan
    exit 1
}

# Verificar Python (opcional — para conversão de ícone)
$python = Get-Command "python" -ErrorAction SilentlyContinue
if (-not $python) {
    $python = Get-Command "python3" -ErrorAction SilentlyContinue
}
$hasPython = $python -ne $null
Write-Host "$(if ($hasPython) { '✔' } else { '⚠ ' })  Python: $(if ($hasPython) { $python.Source } else { 'não encontrado (ícone padrão será usado)' })"

# Verificar WiX (necessário apenas para --type msi)
if ($Type -eq "msi") {
    $candle = Get-Command "candle" -ErrorAction SilentlyContinue
    if (-not $candle) {
        Write-Host ""
        Write-Host "❌  WiX Toolset não encontrado (necessário para gerar .msi)." -ForegroundColor Red
        Write-Host "    Baixe e instale: https://github.com/wixtoolset/wix3/releases" -ForegroundColor Cyan
        Write-Host "    Ou use o formato padrão .exe: .\build-win.ps1 -Type exe" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "✔  WiX Toolset: $(& candle --version 2>&1)"
}

Write-Host ""

# ── 1. Build com Maven ────────────────────────────────────────────────────────
Write-Host "━━━ [1/5] Compilando com Maven ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
& $mvnCmd clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌  Falha no build Maven." -ForegroundColor Red
    exit 1
}
Write-Host "✔  Build concluído → target\agenda-1.0-SNAPSHOT.jar"
Write-Host ""

# ── 2. Gerar ícone .ico ───────────────────────────────────────────────────────
Write-Host "━━━ [2/5] Gerando ícone .ico ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
New-Item -ItemType Directory -Force -Path $PKG_DIR | Out-Null
$icoPath = Join-Path $PKG_DIR "icon.ico"
$pngPath = Join-Path $PKG_DIR "icon.png"

$convertedIcon = $false

if ($hasPython) {
    $pythonExe = $python.Source
    $pyScript = @"
import sys, os
try:
    from PIL import Image
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'Pillow', '--quiet'])
    from PIL import Image

png_path = r'$($pngPath.Replace("\","\\"))'
ico_path = r'$($icoPath.Replace("\","\\"))'

if not os.path.exists(png_path):
    # Gerar ícone básico se não existir
    img = Image.new('RGBA', (256, 256), (15, 32, 80, 255))
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    d.ellipse([20, 20, 236, 236], fill=(60, 110, 200, 255))
    d.text((128, 128), 'A', anchor='mm', fill=(255,255,255,255))
else:
    img = Image.open(png_path).convert('RGBA')

# Converter PNG para ICO com múltiplos tamanhos
sizes = [(16,16), (32,32), (48,48), (64,64), (128,128), (256,256)]
imgs = [img.copy().resize(s, Image.LANCZOS) for s in sizes]
imgs[0].save(ico_path, format='ICO', sizes=sizes, append_images=imgs[1:])
print(f'Icone ICO salvo: {ico_path}')
"@
    $tmpPy = [System.IO.Path]::GetTempFileName() + ".py"
    $pyScript | Set-Content -Path $tmpPy -Encoding UTF8
    & $pythonExe $tmpPy
    Remove-Item $tmpPy -ErrorAction SilentlyContinue
    if (Test-Path $icoPath) {
        Write-Host "✔  Ícone gerado: $icoPath"
        $convertedIcon = $true
    }
}

if (-not $convertedIcon) {
    # Fallback: usar PNG diretamente (jpackage aceita em alguns casos)
    # Ou avisar e continuar
    if (Test-Path $pngPath) {
        Write-Host "⚠  Python não disponível. Usando icon.png diretamente." -ForegroundColor Yellow
        Write-Host "   Para melhor resultado, instale Python: https://python.org/downloads" -ForegroundColor Yellow
        $icoPath = $pngPath
    } else {
        Write-Host "⚠  Nenhum ícone encontrado. Usando ícone padrão do jpackage." -ForegroundColor Yellow
        $icoPath = $null
    }
}
Write-Host ""

# ── 3. Coletar dependências ───────────────────────────────────────────────────
Write-Host "━━━ [3/5] Coletando dependências ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if (Test-Path $INPUT_DIR) { Remove-Item $INPUT_DIR -Recurse -Force }
New-Item -ItemType Directory -Force -Path $INPUT_DIR | Out-Null

# App JAR
Copy-Item "target\agenda-1.0-SNAPSHOT.jar" $INPUT_DIR
Write-Host "  + agenda-1.0-SNAPSHOT.jar"

# JavaFX Windows JARs
$JAVAFX_MODS = @("javafx-base","javafx-graphics","javafx-controls","javafx-fxml","javafx-web","javafx-media","javafx-swing")
foreach ($mod in $JAVAFX_MODS) {
    $jarPath = "$M2\org\openjfx\$mod\$JAVAFX_VER\$mod-$JAVAFX_VER-win.jar"
    if (Test-Path $jarPath) {
        Copy-Item $jarPath $INPUT_DIR
        Write-Host "  + $mod-$JAVAFX_VER-win.jar"
    } else {
        Write-Host "  ⚠  Não encontrado: $jarPath" -ForegroundColor Yellow
        Write-Host "     Tentando baixar via Maven..." -ForegroundColor Yellow
        & $mvnCmd dependency:get `
            "-Dartifact=org.openjfx:${mod}:${JAVAFX_VER}:jar:win" `
            "-Ddest=$INPUT_DIR\$mod-$JAVAFX_VER-win.jar" -q
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  ❌  Falha ao baixar $mod." -ForegroundColor Red
        }
    }
}

# SQLite JDBC
$sqliteJar = "$M2\org\xerial\sqlite-jdbc\3.49.1.0\sqlite-jdbc-3.49.1.0.jar"
if (Test-Path $sqliteJar) {
    Copy-Item $sqliteJar $INPUT_DIR
    Write-Host "  + sqlite-jdbc-3.49.1.0.jar"
} else {
    Write-Host "  ⚠  SQLite JDBC não encontrado: $sqliteJar" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "  Arquivos no input dir:"
Get-ChildItem $INPUT_DIR | ForEach-Object { Write-Host "    $_" }
Write-Host ""

# ── 4. Executar jpackage ──────────────────────────────────────────────────────
Write-Host "━━━ [4/5] Gerando instalador .$Type com jpackage ━━━━━━━━━━━━━━━━━━━"
if (Test-Path $DIST_DIR) { Remove-Item $DIST_DIR -Recurse -Force }
New-Item -ItemType Directory -Force -Path $DIST_DIR | Out-Null

$jpackageArgs = @(
    "--type",          $Type,
    "--name",          $APP_NAME,
    "--app-version",   $VERSION,
    "--vendor",        $VENDOR,
    "--description",   $DESCRIPTION,
    "--win-dir-chooser",
    "--win-menu",
    "--win-shortcut",
    "--win-shortcut-prompt",
    "--win-menu-group", $DISPLAY_NAME,
    "--win-upgrade-uuid", "a4b2c3d4-e5f6-7890-abcd-ef1234567890",
    "--module-path",   $INPUT_DIR,
    "--module",        $MAIN_MODULE,
    "--dest",          $DIST_DIR,
    "--java-options",  "--add-opens=javafx.base/com.sun.javafx.property=com.pessoal.agenda",
    "--java-options",  "-Dfile.encoding=UTF-8",
    "--java-options",  "-Djava.net.useSystemProxies=true"
)

if ($icoPath -and (Test-Path $icoPath)) {
    $jpackageArgs += "--icon"
    $jpackageArgs += $icoPath
}

& jpackage @jpackageArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌  jpackage falhou." -ForegroundColor Red
    if ($Type -eq "msi") {
        Write-Host "    Para .msi é necessário o WiX Toolset 3.x instalado." -ForegroundColor Yellow
        Write-Host "    Tente com -Type exe que não tem dependências externas." -ForegroundColor Yellow
    }
    exit 1
}

# ── 5. Resultado ──────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "━━━ [5/5] Resultado ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
$outFile = Get-ChildItem $DIST_DIR -Filter "*.$Type" | Select-Object -First 1

if ($outFile) {
    $sizeMB = [math]::Round($outFile.Length / 1MB, 1)
    Write-Host ""
    Write-Host "✅  Instalador gerado com sucesso!" -ForegroundColor Green
    Write-Host ""
    Write-Host "    Arquivo : $($outFile.FullName)"
    Write-Host "    Tamanho : $sizeMB MB"
    Write-Host ""
    Write-Host "══════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host "  DISTRIBUIÇÃO:"
    Write-Host ""
    Write-Host "  ✅  O usuário final NÃO precisa instalar Java."
    Write-Host "  ✅  O JRE está embutido no instalador."
    Write-Host "  ✅  Basta enviar o arquivo $($outFile.Name) para o usuário."
    Write-Host ""
    Write-Host "  Para instalar: execute $($outFile.Name) normalmente"
    Write-Host "  O app será instalado em: %LOCALAPPDATA%\$APP_NAME"
    Write-Host "  Atalho criado no Menu Iniciar e Área de Trabalho"
    Write-Host "══════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host ""
} else {
    Write-Host "❌  Arquivo .$Type não encontrado em $DIST_DIR" -ForegroundColor Red
    exit 1
}

