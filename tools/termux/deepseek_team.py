#!/usr/bin/env python3
"""Termux DeepSeek team console with an explicit GitHub hand-off."""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import urllib.error
import urllib.request
from pathlib import Path
from typing import Iterable

API_URL = os.environ.get("DEEPSEEK_API_URL", "https://api.deepseek.com/chat/completions")
MODEL = os.environ.get("DEEPSEEK_MODEL", "deepseek-flash")
REPO = os.environ.get("JARVIS_GITHUB_REPO", "Stahij/jarvis-android")
STATE_DIR = Path(os.environ.get("XDG_CONFIG_HOME", str(Path.home() / ".config"))) / "jarvis-team"
STATE_FILE = STATE_DIR / "deepseek_history.json"
MAX_HISTORY_MESSAGES = 40
MAX_ATTACHMENT_BYTES = 64 * 1024

SYSTEM_PROMPT = """Ты — равноправный участник команды разработки Jarvis Android.
Работай наравне с другими ИИ: предлагай архитектуру, реализуй задачи, анализируй код,
проводить ревью и объясняй компромиссы. Не выдумывай результаты сборок и тестов.
Сначала изучай предоставленный код и требования. Предпочитай небольшие атомарные
изменения, отдельные Git-ветки, тесты и откат. Не меняй подпись APK, applicationId,
критические механизмы безопасности и пользовательские данные без отдельного согласования.
Если контекста недостаточно — прямо перечисли, чего не хватает. Отвечай по-русски."""


def load_state() -> dict:
    try:
        data = json.loads(STATE_FILE.read_text(encoding="utf-8"))
        if isinstance(data, dict):
            data.setdefault("messages", [])
            data.setdefault("seen_comments", [])
            return data
    except (OSError, json.JSONDecodeError):
        pass
    return {"messages": [], "seen_comments": []}


def save_state(state: dict) -> None:
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    tmp = STATE_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    os.chmod(tmp, 0o600)
    tmp.replace(STATE_FILE)
    os.chmod(STATE_FILE, 0o600)


def trim_messages(messages: list[dict]) -> list[dict]:
    return messages[-MAX_HISTORY_MESSAGES:]


def stream_chat(messages: list[dict], api_key: str) -> str:
    payload = {
        "model": MODEL,
        "messages": messages,
        "stream": True,
        "thinking": {"type": "disabled"},
        "max_tokens": 4096,
    }
    request = urllib.request.Request(
        API_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        },
        method="POST",
    )
    answer_parts: list[str] = []
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            for raw_line in response:
                line = raw_line.decode("utf-8", errors="replace").strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    event = json.loads(data)
                except json.JSONDecodeError:
                    continue
                choices = event.get("choices") or []
                if not choices:
                    continue
                delta = choices[0].get("delta") or {}
                piece = delta.get("content")
                if piece:
                    print(piece, end="", flush=True)
                    answer_parts.append(piece)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"DeepSeek API вернул HTTP {exc.code}: {detail[:1200]}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Не удалось подключиться к DeepSeek: {exc.reason}") from exc
    print()
    return "".join(answer_parts)


def run_gh(args: list[str]) -> str:
    if not shutil.which("gh"):
        raise RuntimeError("Не найден GitHub CLI. Установи его командой: pkg install gh")
    proc = subprocess.run(
        ["gh", *args], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    if proc.returncode:
        raise RuntimeError(proc.stderr.strip() or "Команда gh завершилась с ошибкой")
    return proc.stdout.strip()


def transcript_for_github(messages: Iterable[dict]) -> str:
    lines = [
        "> Перед публикацией проверь, что в переписке нет паролей, API-ключей и личных данных.",
        "",
    ]
    for item in messages:
        role = item.get("role", "user").upper()
        content = str(item.get("content", "")).strip()
        if content:
            lines.extend([f"### {role}", "", content, ""])
    return "\n".join(lines)


def print_help() -> None:
    print("""Команды:
  /help             показать эту справку
  /model            показать выбранную модель
  /attach PATH      добавить текстовый файл к следующему сообщению (до 64 КБ)
  /task [ЗАГОЛОВОК] передать текущую задачу в GitHub Issue
  /sync NUMBER      забрать новые комментарии из GitHub Issue в локальный диалог
  /reset            очистить локальную историю диалога
  /quit             выйти

API-ключ задаётся только в переменной окружения DEEPSEEK_API_KEY.
История хранится локально в ~/.config/jarvis-team/ и не отправляется в GitHub,
пока ты явно не вызовешь /task.""")


def main() -> int:
    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        print("Не задан DEEPSEEK_API_KEY. Сначала установи ключ в переменную окружения.")
        print('Пример: export DEEPSEEK_API_KEY="твой_ключ"')
        return 2

    state = load_state()
    pending_context = ""
    print(f"DeepSeek Team Console • {MODEL}")
    print(f"Репозиторий для обмена задачами: {REPO}")
    print("Напиши сообщение или введи /help. История хранится локально.\n")

    while True:
        try:
            user_text = input("Ты > ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nВыход.")
            break
        if not user_text:
            continue
        if user_text == "/quit":
            break
        if user_text == "/help":
            print_help()
            continue
        if user_text == "/model":
            print(f"Модель: {MODEL}\nAPI: {API_URL}")
            continue
        if user_text == "/reset":
            state["messages"] = []
            save_state(state)
            pending_context = ""
            print("Локальная история очищена.")
            continue
        if user_text.startswith("/attach "):
            raw_path = user_text[len("/attach "):].strip()
            path = Path(raw_path).expanduser()
            try:
                if not path.is_file():
                    raise RuntimeError("Это не обычный файл.")
                if path.stat().st_size > MAX_ATTACHMENT_BYTES:
                    raise RuntimeError("Файл больше 64 КБ. Передай только нужный фрагмент.")
                content = path.read_text(encoding="utf-8")
                pending_context = f"\n\n[ФАЙЛ: {path.name}]\n{content}\n[/ФАЙЛ]"
                print(f"Файл добавлен к следующему сообщению: {path.name}")
            except (OSError, UnicodeError, RuntimeError) as exc:
                print(f"Не удалось добавить файл: {exc}")
            continue
        if user_text.startswith("/task"):
            title = user_text[len("/task"):].strip() or "Задача команды ИИ по Jarvis"
            body = (
                "## Переданная задача\n\n"
                "Создано из локальной Termux-консоли DeepSeek Team.\n\n"
                + transcript_for_github(state["messages"][-20:])
            )
            print("Внимание: Issue в публичном репозитории будет виден всем.")
            confirm = input("Опубликовать переписку в GitHub? [y/N] ").strip().lower()
            if confirm not in ("y", "yes", "д", "да"):
                print("Публикация отменена.")
                continue
            try:
                url = run_gh([
                    "issue", "create", "--repo", REPO, "--title", title, "--body", body
                ])
                print(f"Задача передана в GitHub: {url}")
            except RuntimeError as exc:
                print(f"Ошибка GitHub: {exc}")
            continue
        if user_text.startswith("/sync "):
            number = user_text[len("/sync "):].strip()
            if not number.isdigit():
                print("Формат: /sync НОМЕР_ISSUE")
                continue
            try:
                raw = run_gh([
                    "issue", "view", number, "--repo", REPO,
                    "--json", "title,comments"
                ])
                issue = json.loads(raw)
                seen = set(state["seen_comments"])
                imported = 0
                for comment in issue.get("comments", []):
                    marker = comment.get("url") or (
                        str(comment.get("author", {}).get("login", "")) + comment.get("createdAt", "")
                    )
                    if marker in seen:
                        continue
                    author = comment.get("author", {}).get("login", "unknown")
                    content = comment.get("body", "").strip()
                    if content:
                        state["messages"].append({
                            "role": "user",
                            "content": f"[Комментарий GitHub от @{author} в Issue #{number}]\n{content}",
                        })
                        print(f"\nGitHub • @{author}:\n{content}\n")
                        imported += 1
                    seen.add(marker)
                state["seen_comments"] = list(seen)
                state["messages"] = trim_messages(state["messages"])
                save_state(state)
                print(f"Импортировано новых комментариев: {imported}")
            except (RuntimeError, json.JSONDecodeError) as exc:
                print(f"Ошибка синхронизации: {exc}")
            continue

        message = user_text + pending_context
        pending_context = ""
        state["messages"].append({"role": "user", "content": message})
        messages = [{"role": "system", "content": SYSTEM_PROMPT}] + trim_messages(state["messages"])
        print("DeepSeek > ", end="", flush=True)
        try:
            answer = stream_chat(messages, api_key)
        except RuntimeError as exc:
            print(f"\nОшибка: {exc}")
            state["messages"].pop()
            continue
        state["messages"].append({"role": "assistant", "content": answer})
        state["messages"] = trim_messages(state["messages"])
        save_state(state)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
