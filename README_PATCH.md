# Патч: этапы 1–2 (стриминг, «Стоп», LlmService) + документация

Распакуйте в **корень репозитория** с заменой файлов (пути совпадают с репозиторием).

| Файл | Что внутри |
|---|---|
| `app/src/main/cpp/native-lib.cpp` | стриминг, `nativeAbort`, UTF-8 байтами, ошибки исключениями |
| `app/src/main/java/com/stahij/jarvis/NativeLlm.kt` | `generate(...)`, `abort()`, `TokenListener` |
| `app/src/main/java/com/stahij/jarvis/MainActivity.kt` | поток в пузыре, ➤/■, подключение к `LlmEngine` (изменён) |
| `app/src/main/java/com/stahij/jarvis/LlmEngine.kt` | **новый**: ход диалога вне Activity |
| `app/src/main/java/com/stahij/jarvis/LlmService.kt` | **новый**: foreground-сервис, WakeLock, «Стоп» в уведомлении |
| `app/src/main/java/com/stahij/jarvis/ReplyPolicy.kt` | **новый**: итоговый ответ (чистая функция) |
| `app/src/main/java/com/stahij/jarvis/KnowledgeStore.kt` | **новый**: поиск по сохранённым заметкам |
| `app/src/main/AndroidManifest.xml` | сервис, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `configChanges` |
| `docs/*.md` | архитектура, ревью, дорожная карта, чек-лист проверки |

Проверено: C++ собирается на заглушках `jni.h`/`llama.h` без предупреждений; граничные случаи UTF-8 прогнаны тестом; манифест валиден как XML.
**Не проверено:** сборка Kotlin и работа на устройстве. Начните с [docs/TESTING.md](docs/TESTING.md).

Для R8 (когда будет включён) добавьте keep-правило для `com.stahij.jarvis.TokenListener` и методов `NativeLlm`: JNI ищет `onToken` по имени.
