#!/bin/bash

# ============================================================
# deploy-remote.sh - Déployer NEXO sur une machine distante
# ============================================================

if [ $# -lt 1 ]; then
    echo "❌ Usage: $0 <ip_serveur> [port_serveur]"
    echo ""
    echo "Exemples:"
    echo "  $0 192.168.1.100"
    echo "  $0 192.168.1.100 4444"
    echo ""
    exit 1
fi

SERVER_IP="$1"
SERVER_PORT="${2:-4444}"
VIDEO_PORT="${3:-5000}"
AUDIO_PORT="${4:-6000}"

echo "╔═══════════════════════════════════════════════════════╗"
echo "║  🚀 NEXO Chat - Déploiement Distant                  ║"
echo "╚═══════════════════════════════════════════════════════╝"
echo ""
echo "📋 Configuration cible:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  🖥️  Serveur: $SERVER_IP"
echo "  🔌 Port TCP: $SERVER_PORT"
echo "  📹 Port Vidéo: $VIDEO_PORT"
echo "  🎵 Port Audio: $AUDIO_PORT"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Mettre à jour la configuration
CONFIG_FILE=".nexo_config.properties"

cat > "$CONFIG_FILE" << EOF
# Configuration NEXO Chat
# Générée automatiquement par deploy-remote.sh
# Le $(date)

server.host=$SERVER_IP
server.port=$SERVER_PORT
video.port=$VIDEO_PORT
audio.port=$AUDIO_PORT
app.name=NEXO Chat
app.version=1.2.1
EOF

echo "✅ Fichier de configuration créé"
echo ""

# Compiler
echo "🔨 Compilation du projet..."
mvn clean compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation réussie"
else
    echo "❌ Erreur de compilation"
    exit 1
fi

echo ""
echo "✅ Déploiement prêt!"
echo ""
echo "🚀 Pour lancer l'application:"
echo "   mvn clean javafx:run"
echo ""
