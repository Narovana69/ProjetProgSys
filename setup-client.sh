#!/bin/bash

# ============================================================
# setup-client.sh - Test de connexion au serveur NEXO
# ============================================================

echo "╔═══════════════════════════════════════════════════════╗"
echo "║  🔧 NEXO Chat - Vérification Configuration           ║"
echo "╚═══════════════════════════════════════════════════════╝"
echo ""

# Déterminer le répertoire du script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CONFIG_FILE="$SCRIPT_DIR/.nexo_config.properties"

echo "📁 Répertoire du projet: $SCRIPT_DIR"
echo "⚙️  Fichier de configuration: $CONFIG_FILE"
echo ""

# Afficher la configuration actuelle
if [ -f "$CONFIG_FILE" ]; then
    echo "📋 Configuration actuelle:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    grep -v "^#" "$CONFIG_FILE" | grep "=" | head -10
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "💡 Pour modifier la configuration, éditez directement:"
    echo "   nano $CONFIG_FILE"
    echo ""
else
    echo "⚠️  Fichier de configuration non trouvé!"
    echo "   Créez .nexo_config.properties avec:"
    echo "   server.host=localhost"
    echo "   server.port=8080"
    echo "   video.port=5000"
    echo "   audio.port=5001"
    exit 1
fi

# Tester la connexion
echo "🧪 Test de connexion..."
echo ""

CURRENT_HOST=$(grep "^server.host=" "$CONFIG_FILE" | cut -d'=' -f2)
CURRENT_PORT=$(grep "^server.port=" "$CONFIG_FILE" | cut -d'=' -f2)

if [ -z "$CURRENT_HOST" ] || [ -z "$CURRENT_PORT" ]; then
    echo "❌ Configuration invalide - server.host ou server.port manquant"
    exit 1
fi

echo "Tentative de connexion à $CURRENT_HOST:$CURRENT_PORT..."

# Tester avec nc
if command -v nc &> /dev/null; then
    if nc -zv "$CURRENT_HOST" "$CURRENT_PORT" 2>/dev/null; then
        echo "✅ Connexion réussie!"
    else
        echo "⚠️  Serveur indisponible à $CURRENT_HOST:$CURRENT_PORT"
        echo ""
        echo "💡 Suggestions:"
        echo "  1. Vérifiez que le serveur est en cours d'exécution: ./start-server.sh"
        echo "  2. Vérifiez l'IP correcte du serveur: hostname -I"
        echo "  3. Vérifiez que les ports ne sont pas bloqués par le firewall"
    fi
elif ping -c 1 "$CURRENT_HOST" &> /dev/null; then
    echo "✅ Serveur accessible par ping"
else
    echo "⚠️  Impossible de joindre le serveur"
    echo ""
    echo "💡 Vérifiez que l'IP est correcte: hostname -I"
fi

echo ""
echo "✅ Vérification terminée!"
echo ""
echo "🚀 Pour démarrer l'application:"
echo "   ./start-client.sh"
echo ""
