# JavaFX GUI Development Rules

1. Use a simple MVC approach and keep responsibilities separate.

   - FXML/View is responsible for presentation.
   - Controllers handle UI events, bindings, and calls to the existing code for reading and changing data.
   - Keep data access, file I/O, network operations, and external process execution out of controllers.
   - Do not put business rules in the GUI or duplicate logic from other parts of the system.
   - Data access code must not depend on a controller or a specific screen.
   - Do not introduce additional architectural layers merely to satisfy MVC.

2. Follow the Single Responsibility Principle.

   Methods, classes, and packages should have clear responsibilities. Do not create classes that mix UI, operation coordination, data access, and business logic.

3. Choose the simplest solution that meets the current need.

   - Do not split a simple task into many small classes without a practical reason.
   - Do not introduce factories, builders, adapters, interfaces, or DTOs for hypothetical future needs or just to follow a pattern. Each must solve a concrete, existing problem.
   - If a small task appears to require many new production classes, first check whether it can be solved much more simply.

4. Introduce interfaces and inheritance only when justified.

   - Do not create an interface for every class by default. Use one when multiple implementations, implementation substitution, or module boundaries require it, or when it substantially simplifies testing.
   - Use inheritance when there is a natural `is-a` relationship and shared implementation belongs in the base type. Do not create artificial hierarchies just to reuse a few methods.

5. Prefer standard Java and JavaFX features.

   - Do not reimplement functionality already provided by a suitable standard feature or a mature library. Do not add an external dependency for a trivial function.
   - Use standard JavaFX controls and mechanisms such as TableView, ListView, TreeView, cell factories, dialogs, context menus, and layout containers.
   - Keep the application's appearance simple and standard. Do not create custom controls or event systems when JavaFX already handles the task.

6. Use properties, bindings, and observable collections where they simplify UI code.

   Prefer them over manual state synchronization when they make the code simpler and clearer. Do not use bindings for their own sake.

7. Do not block the JavaFX Application Thread.

   - Run lengthy data operations, file I/O, network calls, and external processes off the UI thread.
   - Update JavaFX controls and their associated observable state on the JavaFX Application Thread.
   - Use suitable standard JavaFX mechanisms for background work. Handle errors and support cancellation where needed.

8. Keep CellFactory/Cell focused on display and UI behavior for a cell.

   Do not put data queries, direct domain state changes, or heavy logic inside cells. Pass user actions to a controller handler.

9. Use structured Java types for data with a known structure.

   Prefer a `record` or class over `Map<String, Object>`. Do not create several nearly identical DTOs without a need.

10. Keep changes to existing code proportional to the task.

    - Prefer a local refactor over a complete architectural rewrite when the current structure does not obstruct the task.
    - Do not rewrite working code solely to apply a new pattern, enforce uniformity, or pursue a "cleaner architecture."
