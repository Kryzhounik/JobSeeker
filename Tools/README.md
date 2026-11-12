# JobSeeker Tools

В `Tools` хранятся общий Java-код для нескольких модулей и вспомогательные
процедуры проекта.

## Общий Java-код

`com.seeker.python.JpyRuntime` и `PythonRuntime` предоставляют общую
инициализацию одного интерпретатора JPy и чтение технических путей Python.
Их используют Collector, EasyReasoning, Orchestrator и JavaFX. Закрытие
адаптера отдельного модуля не останавливает общий интерпретатор.

Исходники расположены в `Tools/src/main/java`; общая браузерная инфраструктура
находится в пакете `com.seeker.browser`.

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
фоновой вкладки при HTTP 429 управляет ChatEvaluator; сам классификатор
не зависит от Playwright.

`com.seeker.browser.ManagedBrowser` запускает или подключает общий браузер
и управляет принадлежащими клиенту вкладками. `LinkedInJobPage` содержит
общий детектор Easy Apply и чтение локации LinkedIn-вакансии. Эти классы
используют Applier, ChatEvaluator и Collector через Tools, без зависимостей
между потребителями. Версия зависимости Playwright задаётся в родительском POM.

`BrowserInteraction` предоставляет общее открытие, поиск, ввод, однократный
клик и адаптивное ожидание прогресса. Его используют Applier и ChatEvaluator;
страничные признаки готовности остаются у потребителей. Политика ожидания
и границы описаны в [ARCHITECTURE.md](ARCHITECTURE.md#браузерное-взаимодействие).

`Tools` — Maven-модуль `com.seeker:tools` в серверном reactor `Driver`.
Сборка создаёт тонкий `Tools/tools.jar` без точки входа. ChatEvaluator и
JavaFX используют обычную Maven-зависимость, не компилируют эти исходники.
Перед отдельной сборкой GUI выполнить `mvn -f Driver/pom.xml install`;
скрипты сборки GUI делают это автоматически. Python-скрипты в JAR не входят.

Отложенное решение для обращений к работающей JVM через общий UDS-транспорт
описано в [ARCHITECTURE.md](ARCHITECTURE.md). Общий стиль API и CLI модулей —
в [архитектуре Driver](../Driver/ARCHITECTURE.md).
Реализация находится в бэклоге проекта.

## Вспомогательные процедуры

Здесь также остаются инструкции и скрипты для переноса, резервного копирования,
восстановления и проверки состояния проекта:

- `google_drive_database_sync.md`: default database transfer through Google
  Drive.
- `filter_optimization.md`: inspect rejected vacancies and propose safer early
  filters.
- `filter_database.py`: pass every saved job and its readable text through the
  complete vacancy filter without changing the database.
