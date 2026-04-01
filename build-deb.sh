#!/usr/bin/env bash
# =============================================================================
#  build-deb.sh — Gera pacote .deb da Agenda Científica
# =============================================================================
# Pré-requisitos (todos já presentes no sistema):
#   • JDK 24 com jmods  (~/.jdks/openjdk-24.0.2+12-54)
#   • fakeroot + dpkg-deb          (jpackage usa para criar .deb)
#   • python3 + Pillow              (geração do ícone)
#   • Maven Wrapper (./mvnw)
# =============================================================================
set -euo pipefail

# ── Configurações ─────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

APP_NAME="agenda-cientifica"
DISPLAY_NAME="Agenda Científica"
VERSION="1.0.0"
VENDOR="Pessoal"
DESCRIPTION="Agenda Científica Pessoal — Gestão de tarefas, estudos, protocolos e projetos para cientistas"
CATEGORY="Science"
MAIN_MODULE="com.pessoal.agenda/com.pessoal.agenda.Launcher"
JAVAFX_VER="21.0.6"
M2="${HOME}/.m2/repository"
JDK24="${HOME}/.jdks/openjdk-24.0.2+12-54"
JPACKAGE="${JDK24}/bin/jpackage"
INPUT_DIR="${SCRIPT_DIR}/target/jpackage-input"
DIST_DIR="${SCRIPT_DIR}/dist"
PKG_DIR="${SCRIPT_DIR}/packaging"

# ── Verificações ──────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║       Gerador de Pacote .deb — Agenda Científica         ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

if [[ ! -x "$JPACKAGE" ]]; then
    echo "❌  jpackage não encontrado em: $JPACKAGE"
    echo "    Verifique o caminho do JDK 24 no início deste script."
    exit 1
fi

if [[ ! -d "${JDK24}/jmods" ]]; then
    echo "❌  Diretório jmods não encontrado em: ${JDK24}/jmods"
    echo "    É necessário um JDK completo (não apenas JRE)."
    exit 1
fi

echo "✔  jpackage: $("$JPACKAGE" --version)"
echo "✔  JDK jmods: ${JDK24}/jmods"
echo ""

# ── 1. Build com Maven ────────────────────────────────────────────────────────
echo "━━━ [1/5] Compilando com Maven ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
./mvnw clean package -DskipTests -q
echo "✔  Build concluído → target/agenda-1.0-SNAPSHOT.jar"
echo ""

# ── 2. Gerar ícone ────────────────────────────────────────────────────────────
echo "━━━ [2/5] Gerando ícone ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
mkdir -p "$PKG_DIR"

python3 - <<'PYEOF'
import sys
try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    print("Pillow não instalado; tentando instalar...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow", "--quiet"])
    from PIL import Image, ImageDraw, ImageFont

import math, os

SZ = 512
img = Image.new("RGBA", (SZ, SZ), (0, 0, 0, 0))
d   = ImageDraw.Draw(img)

# Fundo circular gradiente (simulado com ellipse)
for i in range(40, 0, -1):
    r  = int(i / 40 * 15)
    g  = int(i / 40 * 32)
    b  = int(i / 40 * 55)
    a  = 255
    margin = (40 - i) * 5
    d.ellipse([margin, margin, SZ - margin, SZ - margin], fill=(r, g, b, a))

# Círculo de borda
d.ellipse([6, 6, SZ-6, SZ-6], outline=(100, 160, 240, 200), width=4)

# ── Calendário (fundo) ───────────────────────────────────────────────────────
CX, CY, CW, CH = 68, 90, 376, 310
d.rounded_rectangle([CX, CY, CX+CW, CY+CH], radius=16,
                    fill=(20, 42, 80, 240), outline=(80, 130, 220, 180), width=2)

# Cabeçalho do calendário
d.rounded_rectangle([CX, CY, CX+CW, CY+56], radius=16,
                    fill=(60, 110, 200, 255))
# Texto "AGENDA" no cabeçalho
try:
    font_h = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 36)
    font_s = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 22)
    font_t = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 48)
except:
    font_h = ImageFont.load_default()
    font_s = font_h
    font_t = font_h

d.text((CX + CW//2, CY + 28), "AGENDA", anchor="mm",
       fill=(240, 248, 255, 255), font=font_h)

# Grade de dias
rows, cols = 4, 7
cell_w = CW // cols
cell_h = (CH - 66) // rows
for r in range(rows):
    for c in range(cols):
        x0 = CX + c * cell_w + 4
        y0 = CY + 66 + r * cell_h + 4
        x1 = x0 + cell_w - 8
        y1 = y0 + cell_h - 8
        num = r * cols + c + 1
        if num <= 28:
            # Destaque em alguns dias
            if num in (3, 7, 12, 18, 22, 25):
                d.ellipse([x0, y0, x1, y1], fill=(80, 140, 230, 200))
            elif num == 15:
                d.ellipse([x0, y0, x1, y1], fill=(220, 80, 80, 200))
            d.text(((x0+x1)//2, (y0+y1)//2), str(num), anchor="mm",
                   fill=(200, 220, 255, 200), font=font_s)

# ── Ícone de átomo (ciência) ─────────────────────────────────────────────────
AX, AY = 380, 380  # centro do átomo
AR = 70
# Órbitas
for angle in [0, 60, 120]:
    rad = math.radians(angle)
    cos_a, sin_a = math.cos(rad), math.sin(rad)
    # Ellipse rotacionada simulada com arco
    for t in range(0, 360, 3):
        tr = math.radians(t)
        x_e = math.cos(tr) * AR * cos_a - math.sin(tr) * (AR//3) * sin_a
        y_e = math.cos(tr) * AR * sin_a + math.sin(tr) * (AR//3) * cos_a
        px = int(AX + x_e)
        py = int(AY + y_e)
        if 0 <= px < SZ and 0 <= py < SZ:
            img.putpixel((px, py), (100, 180, 255, 200))
# Núcleo
d.ellipse([AX-14, AY-14, AX+14, AY+14], fill=(255, 200, 60, 255))
d.ellipse([AX-8,  AY-8,  AX+8,  AY+8],  fill=(255, 240, 160, 255))

# ── Estrela de destaque ───────────────────────────────────────────────────────
def star_points(cx, cy, r_out, r_in, n=5):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(i * 180 / n - 90)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts

d.polygon(star_points(110, 430, 32, 13), fill=(255, 210, 60, 220))

out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)) if '__file__' in dir() else ".", "packaging", "icon.png")
img.save(out_path)
print(f"✔  Ícone salvo: {out_path}")
PYEOF

echo ""

# ── 3. Coletar dependências ───────────────────────────────────────────────────
echo "━━━ [3/5] Coletando dependências ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"

# App JAR
cp "target/agenda-1.0-SNAPSHOT.jar" "$INPUT_DIR/"
echo "  + agenda-1.0-SNAPSHOT.jar"

# JavaFX Linux JARs (contêm bytecode + .so nativos)
JAVAFX_MODS="javafx-base javafx-graphics javafx-controls javafx-fxml javafx-web javafx-media javafx-swing"
for mod in $JAVAFX_MODS; do
    jar_path="${M2}/org/openjfx/${mod}/${JAVAFX_VER}/${mod}-${JAVAFX_VER}-linux.jar"
    if [[ -f "$jar_path" ]]; then
        cp "$jar_path" "$INPUT_DIR/"
        echo "  + ${mod}-${JAVAFX_VER}-linux.jar"
    else
        echo "  ⚠  Não encontrado: $jar_path (tentando baixar via Maven)"
        ./mvnw dependency:get \
            -Dartifact="org.openjfx:${mod}:${JAVAFX_VER}:jar:linux" \
            -Ddest="${INPUT_DIR}/${mod}-${JAVAFX_VER}-linux.jar" -q || true
    fi
done

# SQLite JDBC
SQLITE_JAR="${M2}/org/xerial/sqlite-jdbc/3.49.1.0/sqlite-jdbc-3.49.1.0.jar"
if [[ -f "$SQLITE_JAR" ]]; then
    cp "$SQLITE_JAR" "$INPUT_DIR/"
    echo "  + sqlite-jdbc-3.49.1.0.jar"
else
    echo "  ⚠  SQLite JDBC não encontrado em $SQLITE_JAR"
fi

echo ""
echo "  Arquivos no input dir:"
ls -1 "$INPUT_DIR"
echo ""

# ── 4. Verificar module-info da app ──────────────────────────────────────────
if ! "${JDK24}/bin/jar" tf "${INPUT_DIR}/agenda-1.0-SNAPSHOT.jar" 2>/dev/null | grep -q "module-info.class"; then
    echo "❌  module-info.class não encontrado no JAR da aplicação!"
    echo "    Verifique a compilação do projeto."
    exit 1
fi
echo "✔  module-info.class presente no JAR da aplicação"
echo ""

# ── 5. Executar jpackage ──────────────────────────────────────────────────────
echo "━━━ [4/5] Gerando pacote .deb com jpackage ━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

"$JPACKAGE" \
    --type                deb \
    --name                "$APP_NAME" \
    --app-version         "$VERSION" \
    --vendor              "$VENDOR" \
    --description         "$DESCRIPTION" \
    --linux-app-category  "$CATEGORY" \
    --linux-shortcut \
    --linux-menu-group    "Science;Education;" \
    --module-path         "$INPUT_DIR" \
    --module              "$MAIN_MODULE" \
    --icon                "${PKG_DIR}/icon.png" \
    --dest                "$DIST_DIR" \
    --java-options        "--add-opens=javafx.base/com.sun.javafx.property=com.pessoal.agenda" \
    --java-options        "-Dfile.encoding=UTF-8" \
    --java-options        "-Djava.net.useSystemProxies=true" \
    2>&1

echo ""
echo "━━━ [5/5] Resultado ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
DEB_FILE=$(ls "${DIST_DIR}"/*.deb 2>/dev/null | head -1)
if [[ -n "$DEB_FILE" ]]; then
    echo ""
    echo "✅  Pacote gerado com sucesso:"
    ls -lh "$DEB_FILE"
    echo ""
    echo "══════════════════════════════════════════════════════════"
    echo "  Para instalar:"
    echo "    sudo dpkg -i ${DEB_FILE}"
    echo ""
    echo "  Para desinstalar:"
    echo "    sudo dpkg -r ${APP_NAME}"
    echo ""
    echo "  Após instalar, o app aparecerá no menu de aplicativos"
    echo "  ou execute: /opt/${APP_NAME}/bin/${APP_NAME}"
    echo "══════════════════════════════════════════════════════════"
else
    echo "❌  Pacote .deb não encontrado em ${DIST_DIR}/"
    echo "    Verifique os erros acima."
    exit 1
fi

