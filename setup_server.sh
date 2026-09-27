#!/bin/bash

# ==============================================================================
# Bljadflix Backend Auto-Setup Script für Ubuntu (Oracle Cloud / GCP / VPS)
# ==============================================================================

set -e # Abbrechen bei Fehlern

# Farben für Terminal-Ausgabe
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}=====================================================${NC}"
echo -e "${CYAN}    Bljadflix Backend Auto-Installer gestartet      ${NC}"
echo -e "${CYAN}=====================================================${NC}"

# 1. Systempakete aktualisieren und Abhängigkeiten installieren
echo -e "\n${YELLOW}[1/6] Aktualisiere Systempakete & installiere Tools...${NC}"
sudo apt-get update -y
sudo apt-get upgrade -y

# Automatische Ja-Bestätigung für iptables-persistent
echo "iptables-persistent iptables-persistent/enable_ipv4 boolean true" | sudo debconf-set-selections
echo "iptables-persistent iptables-persistent/enable_ipv6 boolean true" | sudo debconf-set-selections

sudo apt-get install -y \
    python3 \
    python3-pip \
    python3-venv \
    git \
    curl \
    htop \
    iptables-persistent \
    netfilter-persistent \
    build-essential \
    libxml2-dev \
    libxslt1-dev

# 2. Firewall (iptables) konfigurieren
echo -e "\n${YELLOW}[2/6] Konfiguriere Server-Firewall (Port 8000, 80, 443)...${NC}"
sudo iptables -I INPUT -p tcp --dport 8000 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save || true

if command -v ufw >/dev/null 2>&1; then
    sudo ufw allow 8000/tcp || true
    sudo ufw allow 80/tcp || true
    sudo ufw allow 443/tcp || true
fi

# 3. Pfade und Ordner vorbereiten
INSTALL_DIR="$HOME/bljadflix"
echo -e "\n${YELLOW}[3/6] Richte Projektverzeichnis ein unter: ${INSTALL_DIR}${NC}"

mkdir -p "$INSTALL_DIR"

# Wenn das Skript aus dem geforkten/geklonten Ordner ausgeführt wird, kopieren wir die Dateien
CURRENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ "$CURRENT_DIR" != "$INSTALL_DIR" ] && [ -d "$CURRENT_DIR/backend" ]; then
    echo "Kopiere Projektdateien von $CURRENT_DIR nach $INSTALL_DIR..."
    cp -r "$CURRENT_DIR/"* "$INSTALL_DIR/"
fi

cd "$INSTALL_DIR"

# 4. Python Virtual Environment & Requirements einrichten
echo -e "\n${YELLOW}[4/6] Erstelle Python Virtual Environment (venv)...${NC}"
if [ ! -d "$INSTALL_DIR/venv" ]; then
    python3 -m venv "$INSTALL_DIR/venv"
fi

echo "Installiere Python-Abhängigkeiten..."
"$INSTALL_DIR/venv/bin/pip" install --upgrade pip
if [ -f "$INSTALL_DIR/backend/requirements.txt" ]; then
    "$INSTALL_DIR/venv/bin/pip" install -r "$INSTALL_DIR/backend/requirements.txt"
else
    echo -e "${YELLOW}Hinweis: backend/requirements.txt nicht gefunden, installiere Standard-Pakete...${NC}"
    "$INSTALL_DIR/venv/bin/pip" install fastapi uvicorn requests pydantic beautifulsoup4 lxml apscheduler
fi

# 5. config.json im Hauptverzeichnis erstellen falls nicht vorhanden
echo -e "\n${YELLOW}[5/6] Überprüfe config.json...${NC}"
CONFIG_FILE="$INSTALL_DIR/config.json"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Erstelle Standard config.json..."
    cat <<EOF > "$CONFIG_FILE"
{
  "streaming_source_ip": "127.0.0.1",
  "backend_port": 8000,
  "backend_ip": "0.0.0.0",
  "default_source": "aniworld",
  "indexer_interval_hours": 12
}
EOF
fi

# 6. Systemd-Dienst erstellen & aktivieren
echo -e "\n${YELLOW}[6/6] Richte Systemd-Dienst (Autostart) ein...${NC}"

SERVICE_FILE="/etc/systemd/system/bljadflix.service"

sudo bash -c "cat <<EOF > $SERVICE_FILE
[Unit]
Description=Bljadflix FastAPI Backend Service
After=network.target

[Service]
User=$USER
WorkingDirectory=$INSTALL_DIR/backend
ExecStart=$INSTALL_DIR/venv/bin/python app_backend.py
Restart=always
RestartSec=5
Environment=PYTHONUNBUFFERED=1

[Install]
WantedBy=multi-user.target
EOF"

sudo systemctl daemon-reload
sudo systemctl enable bljadflix.service
sudo systemctl restart bljadflix.service

# IP-Adresse ermitteln
PUBLIC_IP=$(curl -s ifconfig.me || echo "<DEINE-SERVER-IP>")

echo -e "\n${GREEN}=====================================================${NC}"
echo -e "${GREEN}   🎉 Installation erfolgreich abgeschlossen!       ${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo -e "Dein Bljadflix Backend läuft jetzt im Hintergrund."
echo -e "Erreichbar unter: ${CYAN}http://${PUBLIC_IP}:8000${NC}"
echo -e "Admin Dashboard:  ${CYAN}http://${PUBLIC_IP}:8000/admin${NC}\n"
echo -e "Nützliche Befehle:"
echo -e "  • Status anzeigen: ${YELLOW}sudo systemctl status bljadflix${NC}"
echo -e "  • Live-Logs sehen: ${YELLOW}sudo journalctl -u bljadflix -f${NC}"
echo -e "  • Service neustarten: ${YELLOW}sudo systemctl restart bljadflix${NC}"
echo -e "${GREEN}=====================================================${NC}"
