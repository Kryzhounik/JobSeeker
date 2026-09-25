# JobSeeker Backlog

Purpose: small project backlog and current conventions. This is not an
execution contract; the Java orchestrator owns pipeline behavior.

## Now

- Keep the MVP small: Codex reads vacancy text, extracts structured fields, and
  saves them into SQLite.
- Use `Data/jobs.sqlite` as the local prototype database.
- Use `job_view` as the main filtered DB Browser view.
- Use `job_list` as the one-row-per-job overview, including rejected jobs.
- Use `Driver/collector/scan_justjoin.py` for JustJoinIT search and raw downloads.
- Use `Driver/collector/config/linkedin.properties` for the first LinkedIn search URL
  and one-vacancy debug limit.
- Use `Driver/analyzer/candidate_fit/filter.py` for quick candidate-fit checks.
- Use `Driver/analyzer/candidate_fit/config/filter.ini` to turn quick checks on and off.
- Use `Driver/db/job_mapper.py` for canonical JSON <-> SQLite mapping.
- Use `Driver/db/save.py` only as the CLI wrapper for writing final jobs.
- Put Codex-analyzed job JSON under `Data/analyzed/<source>/`.
- Run `Driver/analyzer/candidate_fit/evaluate.md` before `Driver/db/save.py`;
  it updates the same analyzed JSON with `candidate_fit_percent` and
  `candidate_fit_reason`.
- Use `Driver/analyzer/job_interest/config/interest.ini` for technology-interest
  score rules.
- Run `python Driver/analyzer/job_interest/calculate.py --input <json-or-dir>`
  before `Driver/db/save.py`; the save script requires `job_interest` to
  already exist in JSON.
- Use `Driver/db/save.py` only to save fully scored JSON into
  SQLite.
- Use `Driver/analyzer/config/resume.ini` for candidate languages and the
  single scored maps of available remote/relocation locations.
- The Java orchestrator owns the primary workflow through candidate fit,
  job interest, and final database save. Existing Python modules still own the
  implementation of those stages.

## Next

- **HIGH — MONITORING: Преждевременная остановка локации Playwright-коллектором**
  Ранее коллектор мог принять временное отсутствие кнопки `Next` после
  материализации только 7 из 25 карточек за конец локации. Подозрение на связь
  с `lazy` layout не подтвердилось: проблема воспроизводилась и на `classic`.
  Предположительно исправлено: отсутствие `Next` само по себе больше не является
  признаком конца; коллектор ждёт материализацию до 30 секунд и подтверждает
  последнюю страницу по загруженной пагинации. Дополнительно, когда LinkedIn
  показывает общее число результатов, рассчитывается ожидаемое число карточек
  для текущего `start`; недобор записывается как `incomplete_page` и блокирует
  прогон вместо успешного завершения локации. `total_results` и
  `expected_count` сохраняются в диагностике страницы.
  Живой прогон `20260918T060027Z-batch-linkedin` на медленном соединении собрал
  четыре последовательные страницы по 25 карточек и пережил успешный повтор
  загрузки. До последней страницы он не дошёл из-за отдельной ошибки содержимого
  карточки. Статус: наблюдаем следующие прогоны и проверяем, не воспроизведётся
  ли преждевременная остановка снова.
- **HIGH: Гибридное детерминированное извлечение требований**
  Переиспользовать проверенные technology/language patterns контекстного
  фильтра для извлечения однозначных требований до запуска `job_facts`.
  Сохранять найденные факты вместе с исходными evidence-фрагментами, а агенту
  передавать только необработанный или неоднозначный остаток текста. Простое
  предварительное заполнение полей без сокращения агентного input токены не
  экономит. Сначала сравнить результат гибридного и полного агентного разбора
  на одном scope и не удалять agent fallback для неоднозначных требований.
- **HIGH: Заменить обязательный semantic candidate-fit детерминированным scoring**
  Проверить, можно ли убрать отдельный LLM-проход `candidate_fit` для большинства
  вакансий. Он получает уже формализованные `technologies`, `requirement`,
  `level_rank`, роль и логистику и в основном выполняет описанный в prompt
  weighted coverage. Исторически deterministic gate уже дал 390 нулевых
  результатов (`loc`, `tech`, `lang`), а semantic fit среди следующих 428
  вакансий добавил только 20 нулей и для остальных 408 преимущественно вычислил
  ранжирующий процент. В последнем проверенном scope из 39 готовых job-facts 16
  отсеклись deterministic gate, а 23 потребовали бы отдельных candidate-fit
  turns. Поэтому удаление или сильное сокращение этой агентной фазы потенциально
  выгоднее микросокращений prompt и DTO.

  Реализовать объяснимый scoring engine поверх существующего candidate profile:
  - нормализовать aliases (`RESTful APIs -> REST`, `Postgres -> PostgreSQL`);
  - явно различать `AND` и `OR`, для настоящих альтернатив брать лучший match;
  - хранить настраиваемые adjacency/equivalence coefficients, например
    `NATS -> Kafka` и `AWS -> cloud/GCP/Azure`;
  - вычислять coverage по разнице candidate и required level: ориентиры
    `1.0`, `0.75`, `0.5`, `0.25`, `0.0`;
  - считать основной denominator только по `core` и `required`, с большим весом
    `core`; `important`, `desired` и `nice_to_have` использовать лишь как
    ограниченную корректировку, а optional bonus ограничить пятью пунктами;
  - сохранять missing core в denominator и применять явные score caps;
  - генерировать `candidate_fit_reason` из покрытых требований и крупнейших gaps,
    не скрывая нулевые строки.

  Не удалять Sol fallback сразу. Сначала использовать двухконтурный режим:
  детерминированная формула обслуживает уверенно распознанные случаи, а Sol
  вызывается только для предложенного нуля, неизвестного `core`/`required`,
  неоднозначного `AND`/`OR`, пустого denominator или низкой confidence. Это
  защищает от опасного ложного нуля и одновременно убирает агентный вызов для
  большинства обычных вакансий. `role_mismatch` по возможности определять
  раньше; неоднозначный role/track также отправлять в fallback, а не отвергать
  автоматически.

  Проверить формулу без новых LLM-вызовов на сохранённых analyzed JSON и
  существующих fit-результатах. Сравнивать не только точный процент, который у
  LLM псевдоточен, а buckets `0 / 25 / 50 / 75 / 90`, rank correlation, top-N
  выдачу и критические расхождения около пользовательских порогов. Отдельно
  контролировать отсутствие ложных нулей. После shadow-проверки сначала включить
  формулу с Sol fallback, затем решать, можно ли удалить semantic stage целиком.
- **MEDIUM: Сократить job-facts DTO и agent payload без потери evidence**
  После определения судьбы semantic candidate-fit измерить выигрыш от
  сокращения статического job-facts prompt, output DTO и последующих payload.
  Текущий prompt имеет размер около 12.7 KB, а средний analyzed JSON — около
  3.6 KB; output-токены дороже input-токенов. Проверить устранение дублирования
  в `raw_value`, `summary` и `notes`, более компактное представление требований
  и передачу downstream только реально используемых полей. Не удалять audit
  evidence вслепую и не оптимизировать candidate-fit input отдельно, если
  deterministic scoring устранит этот input полностью.
- **LOW: Дедупликация вакансий**
  Investigate dedup for near-identical LinkedIn jobs:
  - compare raw/card/analyzed data for 4441196528, 4441182950,
    4441197535, and 4441183936;
  - decide which fields can identify the same underlying vacancy safely.
- **LOW: Дедупликация по Apply**
  Investigate recruiter/aggregator dedup by Apply destination:
  - check Hired, micro1, Hire Feed, Quik Hire Staffing, and Crossing Hurdles;
  - compare where LinkedIn Apply redirects and whether they point to the same
    external vacancy/applicant system.
- **MEDIUM: Статус `Mistaken`**
  Add `Mistaken` to the allowed job statuses for vacancies where analysis or
  collection produced a wrong result.
- **MEDIUM: Поэтапный анализ**
  Add staged analysis:
  - raw: vacancy downloaded but not analyzed.
  - tech_checked: technology requirements extracted and checked.
  - logistics_checked: remote scope, relocation, language, and location checked.
  - fully_analyzed: factual summary and notes completed.
- **MEDIUM: Перенести существующий GUI на JavaFX**
  Replace the Python/Tkinter GUI and its C# launcher with a JavaFX application.
  Preserve the existing screens and actions, use the current SQLite schema,
  and call the Java collector/orchestrator directly instead of maintaining a
  second workflow implementation in the GUI.
- **MEDIUM: Оценка стоимости размера job-facts batch на естественных прогонах**
  В рамках обычных workflow-прогонов периодически менять
  `vacancies_per_agent` в `Driver/analyzer/config/execution.ini`. После
  накопления прогонов сравнить сохранённые в SQLite метрики расхода для разных
  размеров batch и выбрать настройку по фактической стоимости. Переключать
  значение между неделями или другими сопоставимыми естественными scope; не
  тратить токены на отдельный синтетический или пустой эксперимент. Учитывать
  компромисс между повторной оплатой стартового контекста у коротких групп и
  накоплением cached history у слишком длинных persistent targets.
- **MEDIUM: Параллельные группы job-facts в Java**
  После проверки последовательного Java-контура использовать `parallel_agents`
  из `Driver/analyzer/config/execution.ini` для параллельной обработки групп.
- **MEDIUM: Фоновая проверка закрытых LinkedIn-вакансий**
  Автоматически запускать существующую проверку сохранённых вакансий в фоне,
  без ручного нажатия кнопки, с ограничением частоты и защитой от параллельных
  запусков. До автоматического изменения статусов сравнить ответы LinkedIn
  guest endpoint с браузерной проверкой на выборке открытых и закрытых вакансий
  и оценить ложные результаты. Переводить вакансию в `Closed` только по
  подтверждённо надёжному сигналу; `404`, rate limit, неожиданный HTML и сетевые
  ошибки считать неопределённым результатом.

## Later

- Add more sources after the JustJoinIT flow is comfortable.
- Add optional scheduled execution of the complete Java batch only after
  manual runs are stable, so collection and analysis can start automatically
  at configured times without opening the GUI.

## Rules We Agreed On

- Data stays local under `Data/` and is ignored by Git.
- Do not commit inserts or raw downloaded pages.
- Avoid building a large framework before the MVP proves useful.
- Prefer one clear main view over many temporary display views.
- Technologies use ranks:
  - 1: optional / nice to have / plus.
  - 2: junior / basic / listed required mention.
  - 3: regular / hands-on / commercial or solid experience.
  - 4: advanced / senior.
  - 5: master / expert.
- `req` means required, `opt` means optional.
- `score` is the first sorting field in `job_view`; it combines `fit` and
  `interest`.
