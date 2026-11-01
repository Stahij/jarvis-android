#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BIN="$HOME/bin"
mkdir -p "$BIN"
cp "$ROOT/tools/termux/deepseek_team.py" "$BIN/deepseek_team.py"
cat > "$BIN/deepseek-team" <<'WRAPPER'
#!/data/data/com.termux/files/usr/bin/bash
exec python "$HOME/bin/deepseek_team.py" "$@"
WRAPPER
chmod 700 "$BIN/deepseek_team.py" "$BIN/deepseek-team"

case ":$PATH:" in
  *":$BIN:"*) ;;
  *) echo "Добавь ~/bin в PATH: export PATH=\"\$HOME/bin:\$PATH\"" ;;
esac

echo "Установлено. Команды:"
echo "  pkg install python gh"
echo "  gh auth login"
echo "  export DEEPSEEK_API_KEY='твой_api_key'"
echo "  deepseek-team"
