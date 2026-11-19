# Black-box and White-box Testing in Agentic Development

Black-box and white-box testing have complementary advantages.

White-box testing allows an agent to account for implementation-specific details, cover internal branches and edge cases, and build a more comprehensive regression test suite.

Black-box testing provides an independent interpretation of requirements and contracts. Without seeing the implementation, the agent may be more likely to identify discrepancies between expected and actual behavior rather than reproduce implementation errors in its tests.

Combining both approaches seems promising, but raises questions about token consumption, test duplication, and the independence of the generated tests.

One potentially efficient approach is sequential **Black-box → White-box** testing. The first agent receives only requirements and contracts. The second receives the implementation, requirements, and existing black-box tests, concentrating on additional coverage and implementation-specific scenarios.

This preserves the independence of black-box testing while potentially reducing redundant work during white-box testing.

A relevant study is [CGTest: A hybrid coverage-guided framework for cost-effective unit test generation](https://www.sciencedirect.com/science/article/pii/S0164121226002888) (2026), which explores cost-effective test generation guided by existing coverage.

## Proposed Experiment

Compare five configurations:

1. **Black-box only** — a single pass using a cheaper model.
2. **White-box only** — a single pass using a cheaper model.
3. **Independent Black-box + White-box** — two independent passes using cheaper models, without access to each other's tests.
4. **Sequential Black-box → White-box** — two passes using cheaper models, with the second agent having access to the first agent's tests.
5. **Expensive model, single pass** — use whichever testing approach performs better in configurations 1 and 2.

Evaluation criteria:

- Number of unique defects detected.
- Number of incorrect assertions and false positives.
- Line and branch coverage.
- Total token consumption and monetary cost.
- Number of detected defects relative to cost.

Existing research datasets containing implementations with known defects could potentially provide a reproducible benchmark for this experiment.

The objective is to determine whether combining complementary testing approaches using cheaper models provides better defect detection and cost efficiency than a single pass using a more capable model.

For now, the sequential Black-box → White-box approach appears to be a promising hypothesis rather than an established best practice.
