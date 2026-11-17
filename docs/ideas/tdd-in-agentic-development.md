# TDD in Agentic Development — An Open Question

While developing JobSeeker and building an AI development harness, I started questioning whether adopting Test-Driven Development (TDD) makes sense. At this point, I do not see sufficient grounds for making it a mandatory part of the agentic development process.

This is not a question of whether automated testing is necessary, but whether a particular development sequence — writing tests before implementation — offers meaningful advantages.

## Key Concerns and Hypotheses

**1. The benefits of human-driven TDD may not necessarily translate to AI agents.**

One of the arguments in favor of classical TDD is that writing tests forces developers to better understand requirements, interfaces, and edge cases before starting implementation.

However, it is not obvious that this mechanism is equally effective for LLMs. Given the same specification, can an intermediate test-generation step significantly improve a model's understanding of the task? And if so, is it more effective than an additional requirements analysis and design step?

**2. Error propagation between tests and implementation.**

When tests are written after implementation, there is a risk that an agent will reproduce implementation errors in the tests.

However, TDD does not eliminate this problem; it potentially reverses the direction of error propagation. Misinterpreted requirements may become embedded in the tests, after which the agent deliberately creates an implementation that satisfies incorrect tests.

This concern is particularly relevant in a fully autonomous process where no human review takes place between test generation and implementation.

**3. The impact of tests on architecture.**

In classical TDD, the influence of tests on software design is considered one of the methodology's advantages. However, ease of testing is not necessarily an indicator of architectural quality.

My concern is that deriving contracts and the structure of a solution through sequential test writing may lead an agent to make poorer design decisions than designing them upfront. This comparison concerns two approaches at the same level of design: decisions are either derived through tests or established before writing tests and implementation code.

Furthermore, the initial tests may lock in premature architectural decisions and make them more difficult to reconsider later.

**4. Cost optimization in agentic development.**

One of the goals of a development harness is to efficiently distribute tasks among models with different costs and capabilities.

The assumption is that behavioral requirements and contracts have already been defined in the task specification and implementation plan prepared by a more expensive model. A cheaper model writes the tests based on these requirements rather than independently designing the system's behavior. This appears to be a structurally simpler task than designing and implementing the system, potentially allowing less expensive models to be used without significant loss of quality. When necessary, the resulting tests can be reviewed by a more capable model.

With TDD, test writing becomes part of the design process. Consequently, using a less capable model at this stage may negatively affect architecture and implementation, even if subsequent stages are handled by a more capable model.

## What Existing Research Suggests

At this point, I consider the following two experiments particularly relevant:

- [TDD inside the agent loop — theater or actual value? — Birgitta Böckeler](https://martinfowler.com/articles/exploring-gen-ai/tdd-in-the-agent-loop.html) ([local HTML copy](sources/bockeler-tdd-in-the-agent-loop.html))
- [How well do agents use test/verification techniques? — Dan Luu](https://danluu.com/agentic-testing/) ([local HTML copy](sources/luu-agentic-testing.html))

Local HTML copies were saved on 2026-11-17 for reference if the originals become unavailable. Externally hosted images and interactive charts may still require network access.

Both experiments examine TDD directly within the agentic development loop, without intermediate human intervention. Neither demonstrates a convincing advantage for TDD, while some results suggest potential degradation in implementation quality, test quality, and architectural decisions.

Meanwhile, some studies reporting positive effects from test-first or independent test generation examine a somewhat different mechanism: independent interpretation of requirements or the use of tests as an additional formalization of the specification.

For example, [On the Risk of Coding Before Testing](https://arxiv.org/abs/2607.05139) demonstrates the benefits of independent test generation but does not evaluate the effectiveness of TDD itself. Independent tests can be written before, after, or in parallel with implementation.

An open question remains: does the positive effect come specifically from writing tests, or from an additional independent analysis of the task? Could comparable or better results be achieved by introducing an independent reviewer of requirements, architecture, and implementation instead of generating tests upfront?

## Current Position

For now, I lean toward separating design, implementation, testing, and review rather than combining them into a mandatory Red–Green–Refactor cycle.

This is a working hypothesis, not a final decision. I remain open to adopting TDD for specific categories of tasks or reconsidering the entire approach as new research emerges and I gain more practical experience.

The goal is not to reproduce established human software development practices, but to identify the most effective practices specifically for agentic development.
