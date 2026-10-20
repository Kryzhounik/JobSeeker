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

`Tools` — Maven-модуль `com.seeker:tools` в серверном reactor `Driver`.
Сборка создаёт тонкий `Tools/tools.jar` без точки входа. ChatEvaluator и
JavaFX используют обычную Maven-зависимость, не компилируют эти исходники.
Перед отдельной сборкой GUI выполнить `mvn -f Driver/pom.xml install`;
скрипты сборки GUI делают это автоматически. Python-скрипты в JAR не входят.

## Вспомогательные процедуры

Здесь также остаются инструкции и скрипты для переноса, резервного копирования,
восстановления и проверки состояния проекта:

- `google_drive_database_sync.md`: default database transfer through Google
  Drive.
- `filter_optimization.md`: inspect rejected vacancies and propose safer early
  filters.
- `filter_database.py`: pass every saved job and its readable text through the
  complete vacancy filter without changing the database.
