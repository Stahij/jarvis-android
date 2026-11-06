# Дорожная карта оптимизации

Связанные документы: [CODE_REVIEW_2026-11.md](CODE_REVIEW_2026-11.md) (ID замечаний R-xx), [ARCHITECTURE.md](ARCHITECTURE.md), [TESTING.md](TESTING.md).
Отдельно от `AGENTIC_ENGINEERING_ROADMAP.md` (про агентные возможности): здесь только скорость, стабильность, безопасность и порядок в коде.

## Сделано

### Этап 1: стриминг, «Стоп», UTF-8
- [x] Токены уходят в UI по мере генерации (JNI-колбэк `TokenListener`). R-01
- [x] Кнопка ➤ на время ответа становится ■ «Стоп»; `nativeAbort()` без блокировок. R-01
- [x] Текст между Kotlin и C++ идёт байтами UTF-8, цикл отдаёт только целые символы. R-03
- [x] Ошибки движка это исключения, а не «ответы». R-05
- [x] `configChanges` на `MainActivity`: поворот экрана не пересоздаёт экран. R-02
- [x] Удалён мёртвый код авто-рерайта промпта. R-00
- [x] В историю идёт чистый ответ модели, приписки только в UI (префикс совпадает с KV-кэшем).

### Этап 2: ход диалога живёт вне экрана
- [x] `LlmEngine`: весь ход (контекст инструментов → генерация → итог) в процессе, не в Activity. R-02, R-17
- [x] `LlmService`: foreground-уведомление «Jarvis думает» + «Стоп» + WakeLock. R-02
- [x] Закрыли экран во время ответа: ответ дописывается и сохраняется (`takePendingReply`).
- [x] `POST_NOTIFICATIONS` (Android 13+) запрашивается при первой отправке. R-08 (часть)
- [x] `KnowledgeStore` и `ReplyPolicy` вынесены из `MainActivity` (чистые функции, удобно тестировать). R-15 (часть)

## Дальше

### Этап 2.5: проверка и замеры (делать первым)
- [ ] Сборка в CI, исправление опечаток, которых не мог поймать компилятор у автора патча.
- [ ] Пройти [TESTING.md](TESTING.md) на реальном телефоне.
- [ ] Замер ток/с и `prefill_ms` до и после; таблица в TESTING.md.
- [ ] Замер `-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod` и числа потоков 2/3/4. R-06

### Этап 3: безопасность и контекст
- [ ] Вырезать `<|…|>` из GitHub, веба и заметок перед вставкой в промпт (чистая функция + тесты). R-04
- [ ] Манифест: убрать `RECORD_AUDIO`, `network_security_config` вместо `cleartext=true`, `allowBackup=false`, `BootReceiver exported=false`. R-08
- [ ] `InternetResearchTool`: лимит редиректов, один DNS-резолв, блок CGNAT и ULA, charset, внятная ошибка при капче. R-09
- [ ] `GitHubChatTool`: ветка из `default_branch`, запуск по кнопке, а не по слову «ветк», бюджет в токенах, 1–2 нужных файла вместо восьми. R-07, R-11
- [ ] `UpdateManager`: все подписанты + `versionCode` нового APK больше текущего. R-12

### Этап 4: интерфейс
- [ ] Compose + Material 3 (минимум: XML + RecyclerView); разбить `MainActivity` на `ChatScreen`/`ChatViewModel`/`ModelManager`/`ToolRouter`. R-15
- [ ] Markdown и блоки кода с кнопкой «копировать», «Повторить», индикатор ток/с.
- [ ] Онбординг: проверка места и Wi-Fi перед загрузкой 1,9 ГБ, потом разрешения по необходимости.
- [ ] Один экран настроек вместо диалогов из ☰; тёмная и светлая тема; `strings.xml`; `contentDescription`.
- [ ] Орб: быстрый ввод, поворот, insets, своя иконка уведомления.
- [ ] Озвучка по предложениям во время генерации; убрать `moveTaskToBack` без предупреждения. R-19
- [ ] Персоны в редактируемый JSON.

### Этап 5: структура и процесс
- [ ] История и заметки в Room (FTS для заметок). R-16
- [ ] Проверка и загрузка модели через WorkManager, обновления тоже. R-18
- [ ] gradle wrapper, `.gitignore`, `LICENSE`, version catalog. R-13
- [ ] Тесты: `ReplyPolicy`, `compareVersions`, regex `GitHubChatTool`, sanitize, `isPublicHttpUrl`. CI: lint + unit. R-13
- [ ] CI: релиз по тегу `v*`, actions по SHA, `permissions` на уровне job, `concurrency` без отмены релиза, R8 с keep-правилами для JNI (`TokenListener`, `NativeLlm`). R-10
- [ ] Переписать README и `RELEASE_SIGNING_SETUP.md` под факты; `UncaughtExceptionHandler` → diagnostics; привести в порядок `DiagnosticsActivity`. R-14, R-20

## Задачи для новых участников (небольшие, независимые)

| Задача | Размер | ID |
|---|---|---|
| Удалить неиспользуемый `RECORD_AUDIO` из манифеста | 1 строка | R-08 |
| Добавить gradle wrapper, `.gitignore`, `LICENSE` | мелко | R-13 |
| Юнит-тесты на `ReplyPolicy` и `compareVersions` (чистые функции) | мелко | R-13 |
| Лимит редиректов (например 5) в `InternetResearchTool.request` | мелко | R-09 |
| Функция `sanitizeUntrusted()` для `<\|…\|>` + тесты | мелко | R-04 |
| Переписать README под актуальное состояние | мелко | R-14 |
| `network_security_config` вместо `usesCleartextTraffic` | средне | R-08 |
| Замер ток/с при 2, 3, 4 потоках и с `-march`; результаты в TESTING.md | средне | R-06 |
