# Testing Harness - Implementation and Experiment Plan

**Status:** draft plan for further refinement, not approved architecture or
authorization to begin implementation.

This plan builds on the [autonomous Development Harness idea](autonomous-development-harness-idea.md)
and its [incremental implementation strategy](development-harness-implementation-strategy.md).

## Stage 1. Adapt Codex Proxy

Extend the existing Codex Proxy to launch development agents.

- Separate launch configurations for JobSeeker runtime agents and development agents.
- Retain shared mechanisms for launching Codex CLI, handling errors, and collecting metrics.
- Make models and reasoning effort configurable.
- Support Skills, TOML-defined agents, project instructions, filesystem access, and shell access.
- Require delegated agents to run through the proxy, preventing bypass of token accounting.

**Outcome:** development agents can be launched with token consumption measured for each agent.

## Stage 2. Implement the Testing Component

Use WIO's architectural organization as a starting point without initially adopting its implementation.

- A Skill as the external entry point.
- TOML as the format for defining specialized agents.
- A replaceable internal implementation that does not require changes to the calling agent.
- The option to adopt individual WIO components later.

Move the existing testing guidance from AGENTS.md into the component, preserving the current rules for responsibility allocation, test writing, and test execution.

Initial constraints:

- White-box testing only.
- Work in the same Git working tree as the calling agent.
- The testing agent does not modify production code.
- No additional global orchestration.

**Outcome:** a standalone testing component usable both independently and as part of a future harness.

## Stage 3. Practical Use

Use the testing agent during routine JobSeeker development.

Assess its output quality and identify problems with instructions, context transfer, delegation, and token consumption.

Adjust the implementation as needed based on practical experience.

Do not optimize the agent for specific benchmarks at this stage.

**Outcome:** a testing component that works in practice.

## Stage 4. Prepare the Experimental Environment

Review existing research and open test-generation benchmarks. Select benchmarks that are relatively simple, reproducible, and relevant to the testing agent's purpose.

Define a common evaluation methodology:

| Metric | Purpose |
| --- | --- |
| Defects detected | Primary measure of effectiveness |
| Incorrect assertions | Quality of requirements understanding |
| False positives | Test reliability |
| Line / branch coverage | Coverage completeness |
| Mutation score | Ability to detect defects |
| Token consumption | Total token usage across all participating agents |
| Execution time | Time required to complete the work |
| Stability | Reproducibility of results |

The specific set of metrics depends on what the selected benchmarks support.

**Outcome:** a common, reproducible environment for subsequent comparative experiments.

## Stage 5. Evaluate the Custom Implementation

Compare two configurations:

| Configuration | Description |
| --- | --- |
| Baseline | The original delegation mechanism defined in AGENTS.md |
| Custom | The new testing component based on Skills and TOML |

Initially use identical models and reasoning effort to isolate the effect of workflow organization.

**Outcome:** an assessment of the custom implementation's effectiveness relative to the previous approach.

## Stage 6. Experiment with WIO

Adapt the testing component to use the existing WIO implementation while preserving the external Skill interface.

Compare it using the same benchmarks.

Depending on the results, investigate whether adopting selected parts of WIO would be worthwhile:

- Methodological materials.
- Candidate Scout.
- Strategy Critic.
- Test Reviewer.
- Individual workflow stages.

Not every possible combination needs to be investigated. Let the results determine whether further experiments are needed.

**Outcome:** a choice between the custom implementation, full WIO, or a hybrid solution based on quality and token consumption.

## Stage 7. Experiment with Models

Evaluate how model choice and reasoning effort affect output quality and token consumption.

Identify which tasks within the testing component can be handled by cheaper models without a substantial loss of quality.

**Outcome:** a preferred model configuration for everyday use.

## Stage 8. Experiment with Black-box / White-box Testing

Using the selected implementation, conduct the experiments described in
[black-box-white-box-testing-idea.md](black-box-white-box-testing-idea.md).

Reuse the same benchmarks where possible.

Compare:

| Configuration | Approach |
| --- | --- |
| A | Black-box |
| B | White-box |
| C | Independent Black-box + White-box |
| D | Sequential Black-box -> White-box |
| E | A single pass using a more expensive model |

Evaluate whether the additional quality gained from combined testing justifies the increase in token consumption.

**Outcome:** a preferred testing methodology.

## Stage 9. Determine the Next Direction

Use practical experience and experimental results to identify the next Development Harness component to implement.

Do not design full orchestration until sufficient experience has been gained from developing and using individual components.
