# JobSeeker Backlog

Purpose: small project backlog and current conventions. This is not an
execution contract; use `Driver/WORKFLOW.md` for pipeline behavior.

## Now

- Keep the MVP small: Codex reads vacancy text, extracts structured fields, and
  saves them into SQLite.
- Use `Data/jobs.sqlite` as the local prototype database.
- Use `Driver/WORKFLOW.md` as the public run contract: search, direct URL, and
  reprocess saved raw all converge on the same raw analysis process.
- Use `job_view` as the main filtered DB Browser view.
- Use `job_list` as the one-row-per-job overview, including rejected jobs.
- Use `Driver/collector/scan_justjoin.py` for JustJoinIT search and raw downloads.
- Use `Driver/collector/config/linkedin.properties` for the first LinkedIn search URL
  and one-vacancy debug limit.
- Use `Driver/analyzer/candidate_fit/filter.py` for quick candidate-fit checks.
- Use `Driver/analyzer/candidate_fit/config/filter.ini` to turn quick checks on and off.
- Use `Driver/db/job_mapper.py` for canonical JSON <-> SQLite mapping.
- Use `Driver/db/save.py` only as the CLI wrapper for writing final jobs.
- Use `Driver/analyzer/analyze_job.md` as the analysis skill/prompt.
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
- **HIGH: CLI-вызов оценщиков и скриптовая оркестрация**
  Вынести вызов candidate-fit оценщиков в CLI, чтобы оркестратор мог работать
  скриптово, а расход токенов и результаты каждого вызова считались точно.
- **HIGH: Перенос оркестрации в Java**
  Продолжить переносить в отдельный Java-оркестратор последовательную
  оркестрацию workflow, не расширяя ответственность коллектора. Оркестратор уже
  является точкой входа, делегирует сбор существующему коллектору и владеет
  агентным фильтром по названиям вакансий. Перенос первой функции анализа —
  `job_facts` — завершён. Следующие функции переносить отдельно.

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
- **LOW: Отдельные причины отказа**
  Add filtering fields:
  - `analysis_stage`
  - `reject_reason`
- **MEDIUM: Улучшение оценки соответствия**
  Granulate `candidate_fit_percent` beyond the first language filter.
- **MEDIUM: Расширение оценки интереса**
  Add tech score rules for `job_interest`, up to 99 points.
- **MEDIUM: Оценка стоимости размера job-facts batch**
  В рамках обычных workflow-прогонов периодически менять
  `vacancies_per_agent` в `Driver/analyzer/config/execution.ini`. После
  накопления прогонов сравнить сохранённые в SQLite метрики расхода для разных
  размеров batch и выбрать настройку по фактической стоимости. Отдельный
  синтетический тест для этого не запускать.
- **MEDIUM: Параллельные группы job-facts в Java**
  После проверки последовательного Java-контура использовать `parallel_agents`
  из `Driver/analyzer/config/execution.ini` для параллельной обработки групп.

## Later

- Revisit the archived Codex App Server transport experiment in
  `Driver/codex_proxy/app_server_experiment/` if CLI process overhead becomes
  a blocking problem. Before enabling it, prove nested orchestration and a
  complete pipeline run while keeping backend selection behind the proxy.
- Only add deterministic extraction later if it clearly removes cost without
  creating a growing pile of fragile wording rules.
- Add more sources after the JustJoinIT flow is comfortable.
- Add scheduling only after manual runs are useful.
- Add stale-vacancy cleanup.
- Consider a small UI only after DB Browser stops being enough.

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
