# Development Harness — Implementation Strategy

While exploring agentic development and designing a Development Harness for JobSeeker, I have come to the conclusion that implementing the entire development workflow upfront may be premature.

This article complements the [original idea of an autonomous Development Harness](autonomous-development-harness-idea.md) by outlining the rationale for its implementation strategy rather than a concrete implementation plan.

## Why Not Adopt an Existing Development Harness?

Increasing the number of interacting agents and development stages introduces additional token consumption. Repeated context processing, intermediate result transfers, and review cycles may cause token consumption to grow nonlinearly. A substantial increase in token consumption when moving to multi-agent systems is reported, for example, in [How we built our multi-agent research system — Anthropic (2025)](https://www.anthropic.com/research/multi-agent-research-system).

Practical experiments with existing open-source frameworks suggest that increasing workflow complexity does not necessarily produce proportional improvements in results ([Spec-Driven Development: 18 tools put to the test, 2026](https://blog.kiruban.fr/en/posts/spec-driven-development-tools-benchmark/)). Their economic efficiency, however, depends not only on token consumption but also on the nature of the tasks, the quality of the results, and the reduction in human involvement.

In enterprise development, the high cost of potential errors may justify considerable additional spending on independent verification. In the case of JobSeeker, the budget is limited, and the consequences of most errors are less critical and relatively easy to reverse. Whether heavyweight frameworks are justified for this particular project is therefore not yet clear. Furthermore, integrating and configuring such frameworks for an existing project may require significant time and token consumption upfront.

Existing frameworks also differ substantially in their architecture, workflow organization, and allocation of responsibilities among agents, making individual components difficult to interchange ([From Prompt to Process: a Process Taxonomy and Comparative Assessment of Frameworks Supporting AI Software Development Agents, 2026](https://arxiv.org/abs/2606.04967)). Attempting to achieve universal compatibility at this stage may introduce unnecessary complexity.

At the same time, the observed decline in the cost of achieving comparable levels of AI performance ([The plunging price of thought — Epoch AI, 2026](https://epoch.ai/publications/the-plunging-price-of-thought)) suggests that more complex solutions may become economically attractive in the future.

For now, I therefore lean toward gradually building a lightweight, cost-efficient Development Harness while retaining the possibility of adopting individual components or switching to an existing solution later.

## Why Bottom-Up?

The reliability, effectiveness, and cost efficiency of individual LLM agents remain difficult to predict. Combining multiple agents introduces additional problems related to context transfer, coordination, error propagation, and verification. These failure modes are examined in [Why Do Multi-Agent LLM Systems Fail? (2025)](https://arxiv.org/abs/2503.13657).

Building a complete development pipeline before validating its individual components may result in a complex system whose fundamental building blocks do not work reliably. Moreover, the benefits and costs of agent composition depend on the nature of the task and the coordination architecture, as demonstrated in [Towards a Science of Scaling Agent Systems (2025)](https://arxiv.org/abs/2512.08296).

When such a system produces poor results, identifying the responsible component becomes substantially more difficult. The importance of evaluating individual agent behaviors in addition to end-to-end performance is discussed in [The Anatomy of Harness Engineering — Google (2026)](https://developers.googleblog.com/en/the-anatomy-of-harness-engineering-how-to-evaluate-iterate-and-guard-ai-coding-agents/).

The proposed approach is therefore to implement individual components, evaluate their effectiveness and cost independently, and gradually integrate them into a larger development workflow. This is consistent with the recommendation to start with simple, composable approaches and introduce additional complexity only when justified by measurable improvements ([Building Effective Agents — Anthropic, 2024](https://www.anthropic.com/research/building-effective-agents)).

An independently functioning component can provide practical value on its own, even if the complete harness is never built.

These publications provide practical recommendations and empirical evidence concerning the risks of agent composition and premature complexity. However, they do not directly establish the superiority of a bottom-up approach for the proposed Development Harness architecture.

## Why Start with Testing?

Among the potential components of a Development Harness, automated testing appears to be one of the simplest, safest, and most objectively verifiable functions to automate.

The main reasons are:

- **Limited scope.** The testing agent operates within an existing implementation, requirements, and contracts.
- **Limited architectural influence.** Testing depends on the architecture but does not normally determine it.
- **Low coupling.** A testing component can operate independently of most other components of the future harness.
- **Reversibility.** Incorrect or low-value tests can generally be removed or rewritten without substantial changes to production code.
- **Limited blast radius.** Errors made by a testing agent are less likely to propagate throughout the development process.
- **Objective verification.** Defect detection, coverage, and mutation testing provide measurable evaluation criteria.
- **Short feedback loop.** The agent can receive immediate feedback from compilers, test runners, and other automated tools.
- **Replaceability.** Alternative testing workflows, agents, and models can be evaluated without redesigning the entire harness.

Other potential components, such as requirements analysis and decomposition, architectural design, implementation, acceptance review, and orchestration, appear to involve greater uncertainty, broader responsibilities, or more significant consequences of incorrect decisions.

An acceptance reviewer, for example, may need to recognize that an architectural decision is fundamentally incorrect even when the developer has implemented it correctly and all automated tests pass. Despite potentially operating in read-only mode, such an agent has substantial influence over the development process.

Testing is not necessarily a trivial task for an LLM. The test oracle problem remains relevant, and incorrect tests may reinforce incorrect behavior or create false confidence. However, understanding expected behavior is also necessary for development, architectural design, and review.

Testing appears to offer a more favorable combination of task complexity, safety, verifiability, and reversibility. [SWT-Bench: Testing and Validating Real-World Bug-Fixes with Code Agents (2024)](https://arxiv.org/abs/2406.12952) characterizes test generation as a more structured task than general code generation and demonstrates opportunities for quantitative evaluation.

The choice of testing as the first component remains an engineering hypothesis rather than an established research conclusion.
