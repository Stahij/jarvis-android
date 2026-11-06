# Патч: этап 3 (безопасность и контекст) — поверх этапов 1–2

Распаковать в корень репозитория с заменой. Пути совпадают с репозиторием.

## Изменённые файлы
- `app/src/main/java/com/stahij/jarvis/PromptSanitizer.kt`
- `app/src/main/java/com/stahij/jarvis/ContextBudget.kt`
- `app/src/main/java/com/stahij/jarvis/Versions.kt`
- `app/src/main/java/com/stahij/jarvis/UpdateManager.kt`
- `app/src/main/java/com/stahij/jarvis/GitHubChatTool.kt`
- `app/src/main/java/com/stahij/jarvis/InternetResearchTool.kt`
- `app/src/main/java/com/stahij/jarvis/GitHubRepositoryActivity.kt`
- `app/src/main/java/com/stahij/jarvis/GitHubAccessActivity.kt`
- `app/src/main/java/com/stahij/jarvis/KnowledgeStore.kt`
- `app/src/main/cpp/native-lib.cpp`

## Новые файлы
- `app/src/main/java/com/stahij/jarvis/PromptSanitizer.kt`
- `app/src/main/java/com/stahij/jarvis/ContextBudget.kt`
- `app/src/main/java/com/stahij/jarvis/Versions.kt`
- `tests/native/` (тест склейки токенов без NDK)

## Что изменилось по смыслу
- Служебные токены из чужого текста больше не превращаются в настоящие (R-04, частично).
- GitHub и заметки режутся по бюджету токенов (R-07).
- Веб-инструмент: редиректы ≤ 5, CGNAT/ULA, charset, понятная ошибка капчи (R-09).
- GitHub-инструмент берёт ветку из API (R-11, частично).
- Обновления: все подписанты, версия APK, префикс URL релиза (R-12).

## Что НЕ проверено
- Kotlin не компилировался. C++ собирается на заглушках, тест склейки токенов проходит на подставном токенайзере.
- Настоящий словарь Qwen и поведение шаблона проверяются только на устройстве (TESTING 18–26).

Начните со сборки CI, затем TESTING.md.
