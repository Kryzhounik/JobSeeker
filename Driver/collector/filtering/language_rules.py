"""Code-owned rules for deterministic human-language requirement extraction."""

from __future__ import annotations


# These are parser vocabulary, not runtime settings. Canonical names match the
# language names used by resume.ini and the post-agent candidate-fit filter.
LANGUAGE_ALIASES: dict[str, tuple[str, ...]] = {
    "English": (
        "англійська мова",
        "англійська",
        "англійської",
        "англійською",
        "английский язык",
        "английский",
        "английского",
        "английским",
        "język angielski",
        "angielski",
        "angielskiego",
        "angielskim",
        "limba engleză",
        "engleză",
        "ინგლისური",
        "англійская мова",
        "англійская",
        "ағылшын тілі",
        "językiem angielskim",
    ),
    "Ukrainian": (
        "українська мова",
        "українська",
        "української",
        "українською",
        "українську",
        "украинский язык",
        "украинский",
        "украинского",
        "украинским",
    ),
    "Russian": (
        "русский язык",
        "русский",
        "русского",
        "русским",
        "русском",
        "російська мова",
        "російська",
        "російської",
        "російською",
    ),
    "Belarusian": (
        "беларуская мова",
        "беларуская",
        "беларускай",
        "беларускую",
        "білоруська мова",
        "білоруська",
        "белорусский язык",
        "белорусский",
    ),
    "Polish": (
        "język polski",
        "polski",
        "polskiego",
        "polskim",
        "językiem polskim",
        "польська мова",
        "польська",
        "польської",
        "польською",
        "польский язык",
        "польский",
        "польского",
        "польским",
    ),
    "Romanian": (
        "limba română",
        "română",
        "române",
        "румунська мова",
        "румунська",
        "румынский язык",
        "румынский",
    ),
    "French": (
        "langue française",
        "français",
        "française",
        "język francuski",
        "francuski",
        "французский язык",
        "французский",
        "французька мова",
        "французька",
        "limba franceză",
        "franceză",
    ),
    "German": (
        "Deutsch",
        "deutsche Sprache",
        "język niemiecki",
        "języka niemieckiego",
        "językiem niemieckim",
        "niemiecki",
        "niemieckim",
        "немецкий язык",
        "немецкий",
        "німецька мова",
        "німецька",
        "limba germană",
        "germană",
    ),
    "Georgian": (
        "ქართული ენა",
        "ქართული",
        "грузинська мова",
        "грузинська",
        "грузинский язык",
        "грузинский",
    ),
    "Kazakh": (
        "қазақ тілі",
        "қазақша",
        "қазақ тілін",
        "казахська мова",
        "казахська",
        "казахский язык",
        "казахский",
    ),
}

CEFR_LEVELS = ("A1", "A2", "B1", "B2", "C1", "C2")

EXPLICIT_REQUIREMENT_TEMPLATES = (
    r"{language}\s*\(\s*{level}\s*(?:/|\bor\b|[-\u2013\u2014])\s*{alternative_level}\s*\)",
    r"{language}\s*[:\-–—]\s*{level}",
    r"{language}\s+{level}",
    r"{language}\s+(?:(?:communication|language)\s+)?skills?\s*(?:\([^\)\r\n]{1,80}\)\s*)?at\s+(?:an?\s+)?{level}\s+level(?:\s+or\s+(?:higher|above))?",
    r"{level}\s+level\s+(?:in|of)\s+{language}",
    r"{language}\s*\(\s*{level}\s+level\s*\)",
    r"{language}\s+(?:language\s+)?(?:at\s+)?{level}\s+level",
    r"(?:minimum|at\s+least)\s+{level}\s+{language}\s+(?:language\s+)?level",
    r"{level}\s+{language}\s+(?:language\s+)?level",
    r"{language}\s+(?:мова\s+)?(?:на\s+)?рівн(?:і|я)\s*{level}",
    r"рівень\s+{language}\s*[:\-–—]?\s*{level}",
    r"{language}\s+(?:язык\s+)?(?:на\s+)?уровн(?:е|я)\s*{level}",
    r"уровень\s+{language}\s*[:\-–—]?\s*{level}",
    r"{language}\s+na\s+poziomie\s+{level}",
    r"poziom\s+{language}\s*[:\-–—]?\s*{level}",
    r"{language}\s+la\s+nivel(?:ul)?\s+{level}",
    r"nivel(?:ul)?\s+{level}\s+(?:de\s+)?{language}",
    r"{language}\s+(?:мова\s+)?на\s+ўзроўн(?:і|ю)\s*{level}",
    r"{language}\s+{level}\s+деңгей(?:інде|і)?",
    r"{language}[^\r\n]{0,60}\(\s*min\.?\s*{level}\s*\)",
)

EXPLICIT_LANGUAGE_LIST_TEMPLATES = (
    r"{level}\s+level\s+(?:in|of)\s+{languages}",
    r"(?:minimum|at\s+least)\s+{level}\s+{languages}\s+(?:language\s+)?level",
    r"{level}\s+{languages}\s+(?:language\s+)?level",
    r"{languages}\s+(?:languages?\s+)?(?:at\s+)?{level}\s+level",
    r"вільн(?:е|о)\s+володінн(?:я|і)\s+{languages}\s+мовами\s*\(\s*{level}",
    r"znajomość\s+{languages}\s+na\s+poziomie\s+{level}",
    r"{languages}\s+na\s+poziomie\s+{level}",
)

IMPLIED_LEVEL_TEMPLATES: dict[str, tuple[str, ...]] = {
    "communication_skills": (
        r"\b{language}\s+communication\s+skills?\b",
        r"\b(?:written\s+and\s+verbal|verbal\s+and\s+written)\s+communication\s+in\s+{language}\b",
    ),
    "good": (
        r"\b(?:good|very\s+good)\s+(?:(?:spoken|written|verbal|oral|business)(?:\s+and\s+(?:spoken|written|verbal|oral))?\s+)?{language}(?:\s+(?:communication|language))?(?:\s+skills?)?\b",
        r"\b(?:good|very\s+good)\s+command\s+of\s+{language}\b",
        r"\bgood\s+(?:(?:written|verbal|spoken|oral)(?:\s+and\s+(?:written|verbal|spoken|oral))?\s+)?communication\s+skills?\s+in\s+{language}\b",
        r"\bgood\s+knowledge\s+of\s+{language}\b",
        r"\bbardzo\s+dobr(?:a|y|e)\s+znajomość\s+(?:języka\s+)?{language}\b",
    ),
    "strong": (
        r"\bstrong\s+(?:(?:spoken|written|verbal|oral|business)(?:\s+and\s+(?:spoken|written|verbal|oral))?\s+)?{language}(?:\s+(?:communication|language))?(?:\s+skills?)?\b",
        r"\bstrong\s+command\s+of\s+{language}\b",
        r"\bstrong\s+(?:(?:written|verbal|spoken|oral)(?:\s+and\s+(?:written|verbal|spoken|oral))?\s+)?communication\s+skills?\s+in\s+{language}\b",
        r"\bstrong\s+knowledge\s+of\s+{language}\b",
        r"\bstrong\s+command\s+of\s+(?:the\s+)?{language}(?:\s+language)?\b",
        r"\bсильн(?:а|е|і)\s+(?:письмов(?:а|ої)\s+)?{language}\b",
    ),
    "proficient": (
        r"\bproficient\s+in\s+{language}\b",
        r"\bproficiency\s+in\s+{language}\b",
    ),
    "professional": (
        r"\b(?:professional|working)\s+(?:level|proficiency)\s+(?:of|in)\s+{language}\b",
    ),
    "advanced": (
        r"\badvanced\s+proficiency\s+in\s+{language}",
        r"(?<!intermediate-)\badvanced\s+level\s+of\s+{language}(?:\s+language)?\b",
        r"\b{language}(?:\s+language)?\s*[:\-–—]\s*advanced\b",
    ),
    "fluent": (
        r"\bfluency\s+in\s+{language}",
        r"\bfluent\s+in\s+{language}",
        r"\bfluent\s+{language}\s+skills?\b",
        r"\bfluent\s+{language}\b",
        r"\bfluent\s+(?:written\s+and\s+spoken|spoken\s+and\s+written|business)\s+{language}\b",
        r"\bfluent\s+communication\s+in\s+{language}\b",
        r"\b{language}\s+fluency\b",
        r"\bвільн(?:е|о)\s+володінн(?:я|і)\s+{language}\b",
        r"\bсвободн(?:ое|о)\s+владени(?:е|я)\s+{language}\b",
        r"\bbiegł(?:a|e)\s+znajomość\s+(?:języka\s+)?{language}\b",
        r"\bfluență\s+în\s+{language}\b",
        r"\bсвабоднае\s+валоданне\s+{language}\b",
    ),
    "excellent": (
        r"\bexcellent\s+(?:(?:spoken|written|verbal|oral)(?:\s+and\s+(?:spoken|written|verbal|oral))?\s+)?{language}(?:\s+(?:communication|language))?(?:\s+skills?)?\b",
        r"\bexcellent\s+(?:command|knowledge)\s+of\s+{language}\b",
        r"\bexcellent\s+(?:verbal\s+and\s+written\s+)?communication\s+skills\s+in\s+{language}\b",
    ),
    "native": (
        r"\bnative\s+or\s+near-native\s+{language}(?:\s+proficiency)?\b",
        r"\bnear-native\s+{language}(?:\s+proficiency)?\b",
        r"\bnative\s+{language}(?:\s+proficiency)?\b",
    ),
    "bilingual": (r"\bbilingual\s+{language}(?:\s+proficiency)?\b",),
}

IMPLIED_LEVEL_LANGUAGE_LIST_TEMPLATES: dict[str, tuple[str, ...]] = {
    "communicative": (
        r"\bcommunicative\s+proficiency\s+in\s+(?:both\s+)?{languages}\b",
        r"\bcommunicative\s+{languages}\b",
    ),
    "good": (
        r"\b(?:good|very\s+good)\s+command\s+of\s+{languages}\b",
        r"\b(?:good|very\s+good)\s+{languages}(?:\s+(?:communication|language))?(?:\s+skills?)?\b",
    ),
    "strong": (
        r"\bstrong\s+command\s+of\s+{languages}\b",
        r"\bstrong\s+{languages}(?:\s+(?:communication|language))?(?:\s+skills?)?\b",
    ),
    "proficient": (r"\bproficiency\s+in\s+{languages}\b",),
    "fluent": (
        r"\bfluency\s+in\s+(?:both\s+)?{languages}\b",
        r"\bfluent\s+in\s+{languages}\b",
        r"\bfluent\s+{languages}\b",
        r"\bвільн(?:е|о)\s+володінн(?:я|і)\s+{languages}\b",
        r"\bсвободн(?:ое|о)\s+владени(?:е|я)\s+{languages}\b",
        r"\bbiegle\s+posługujesz\s+się\s+{languages}\b",
    ),
    "excellent": (
        r"\bexcellent\s+{languages}(?:\s+(?:communication|language))?(?:\s+skills?)?\b",
    ),
    "native": (
        r"\bnative\s+or\s+near-native\s+{languages}(?:\s+proficiency)?\b",
    ),
}

# Product policy: a known language explicitly marked as mandatory without a
# level is treated as C2. A more specific rule wins for the same language.
REQUIRED_WITHOUT_LEVEL_TEMPLATES = (
    r"\b{language}(?:\s+language)?\s+(?:is\s+)?(?:required|mandatory)\b",
    r"\b(?:required|mandatory)\s+(?:language\s*:\s*)?{language}\b",
    r"\b{language}(?:\s+мова)?\s+(?:є\s+)?обов['’]?язков(?:а|ою)\b",
    r"\b{language}(?:\s+язык)?\s+обязател(?:ен|ьный)\b",
    r"\b{language}\s+(?:jest\s+)?wymagan(?:y|a|e)\b",
    r"\b{language}\s+(?:este\s+)?obligatori(?:u|e)\b",
    r"\b{language}(?:\s+мова)?\s+абавязков(?:ая|ы)\b",
)
REQUIRED_WITHOUT_LEVEL_LANGUAGE_LIST_TEMPLATES = (
    r"\b{languages}\s+(?:are\s+)?(?:required|mandatory)\b",
)

OPTIONAL_SIGNALS = (
    r"\bnice\s+to\s+have\b",
    r"\boptional\b",
    r"\bwould\s+be\s+a\s+(?:big\s+)?plus\b",
    r"\bnot\s+required\b",
    r"\bpreferred\b",
)
