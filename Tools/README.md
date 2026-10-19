# JobSeeker Tools

В `Tools` хранятся общий Java-код для нескольких модулей и вспомогательные
процедуры проекта.

## Общий Java-код

Исходники расположены в `Tools/src/main/java`, пакет — `com.seeker.tools`.

Сюда помещается код, который действительно используется несколькими модулями.
Код конкретного модуля остаётся в этом модуле. Утилиты не должны зависеть от
своих потребителей: GUI, Applier или ChatEvaluator. Направление зависимости —
от потребителя к Tools; обратные импорты недопустимы.

Сейчас здесь находится `LinkedInJobAvailability`: HTTP-запрос и классификация
страницы LinkedIn-вакансии. Её используют кнопка `Check LinkedIn` в JavaFX
и ChatEvaluator перед отправкой ссылки в чат. Класс использует только JDK
и не обращается к базе вакансий.
Для браузерной проверки классификатор также принимает видимый текст:
признаки закрытия ищутся в нём, а не в скрытых HTML-шаблонах. Открытием
фоновой вкладки при HTTP 429 управляет ChatEvaluator; Tools не зависит
от Playwright или управления браузером.

Отдельного Maven-проекта и собственного JAR у Tools сейчас нет.
[ChatEvaluator](../ChatEvaluator/pom.xml) и [JavaFX GUI](../GUI/JavaFX/pom.xml)
подключают каталог исходников через `build-helper-maven-plugin:add-source`.
Каждая сборка компилирует общий код вместе со своими классами; после его
изменения нужно пересобрать использующие его модули.

## Вспомогательные процедуры

Здесь также остаются инструкции и скрипты для переноса, резервного копирования,
восстановления и проверки состояния проекта:

- `google_drive_database_sync.md`: default database transfer through Google
  Drive.
- `filter_optimization.md`: inspect rejected vacancies and propose safer early
  filters.
- `filter_database.py`: pass every saved job and its readable text through the
  complete vacancy filter without changing the database.
