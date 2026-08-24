# Human-language proficiency normalization

## Stable anchors

| Level | Rank | Semantic anchors |
| --- | ---: | --- |
| A1 | 1 | beginner |
| A2 | 2 | elementary, pre-intermediate |
| B1 | 3 | intermediate |
| B2 | 4 | upper-intermediate, professional working proficiency |
| C1 | 5 | advanced, full professional proficiency |
| C2 | 6 | native, bilingual, native-level proficiency |

Use the anchors semantically, not as exact string patterns. Normalize equivalent
word forms and phrasing to the corresponding CEFR level.

## Ambiguous wording

| Wording | Allowed range | Default |
| --- | --- | --- |
| communicative English | B1-B2 | B1 |
| English communication skills | B1-B2 | B1 |
| good English / good command | B1-B2 | B2 |
| very good English | B2-C1 | B2 |
| strong English / strong command | B2-C1 | B2 |
| proficient / proficiency | B2-C1 | B2 |
| excellent English / excellent command | B2-C1 | C1 |
| fluent / fluency | B2-C1 | C1 |
| near-native | C1-C2 | C2 |

For ambiguous wording, return one level from the allowed range:

- Start from the default when the vacancy provides no useful context.
- Prefer the upper bound when the role or stated duties are language-heavy:
  people or team leadership, regular client or stakeholder communication,
  workshops, negotiation, presentations, sales, support, or substantial writing.
- Prefer the lower bound for an individual-contributor technical role with no
  language-heavy duties, especially when the vacancy explicitly describes a
  local or non-English-speaking working environment.
- Do not raise the level merely because the vacancy is senior. A team-lead role
  is an upper-bound signal because of its communication duties, not its rank.
- Never choose a level outside the wording's allowed range. Explicit CEFR levels
  always override this table.

Project policy currently keeps `fluent` at C1 by default, although market usage
is ambiguous and often means B2/B2+. The deterministic pre-agent filter also
keeps it at C1. See `language_levels_research.txt` before changing this mapping.
