"""Code-owned rules for deterministic technology requirement filtering."""

from __future__ import annotations


# Every hard template must bind the blocked technology through {technology}.
# {years} is the shared expression for ranges starting at two years.
HARD_REQUIREMENT_TEMPLATES = (
    r"\badvanced\s+{technology}\b",
    r"\bproficiency\s+in\s+(?:(?:developing|building|creating|designing|implementing)\b[^.;:\r\n]{0,120}\b(?:using|with)\s+)?{technology}",
    r"\bproficient\s+in\s+{technology}",
    r"\bfluent\s+(?:in\s+)?(?:contest\s+)?{technology}",
    r"\bexpertise\s+in\s+{technology}",
    r"\bproven\s+knowledge\s+(?:of|in)\s+{technology}",
    r"\bgood\s+understanding\s+of\s+{technology}",
    r"\bproven\s+experience\s+in\s+{technology}\s+development\b",
    r"\bproven\s+hands[-\s]on\s+experience\s+in\s+(?:[\w-]+\s+){0,4}(?:development|engineering)\s+(?:with|in|using)\s+{technology}",
    r"\bsolid\s+{technology}\s+proficiency\b",
    r"\bstrong\s+{technology}\s*(?=[.;:\r\n]|$)",
    r"\bstrong\s+[^.;:\r\n]{0,120}{technology}[^.;:\r\n]{0,120}\bskills?\b",
    r"\bstrong\s+(?:[\w-]+\s+){0,4}(?:development|programming)\s+experience\s+(?:with|in|using)\s+{technology}",
    r"\bstrong\s+hands[-\s]on\s+experience\s+(?:with|in|using)\s+{technology}",
    r"\bhands[-\s]on\s+experience\s+(?:supporting|developing)(?:\s+(?:or|and)\s+(?:supporting|developing))?\s+{technology}",
    r"\b(?:deep\s+)?hands[-\s]on\s+expertise\s+(?:with|in|using)\s+{technology}",
    r"\bskills?\s+in\s+{technology}",
    r"\bexperience\s+(?:developing|working|programming|coding)(?:\s+[\w-]+){0,4}\s+(?:with|in|using)?\s*{technology}",
    r"{technology}\s+(?:programming\s+|development\s+)?skills?\b",
    r"{technology}\s+(?:development\s+|programming\s+)?experience\b",
    r"{technology}[-\s]first\b",
    r"{technology}\s+(?:is|are)\s+required\b",
    r"\brequired\s+(?:experience|proficiency|expertise|skills?)\s+(?:with|in|using)\s+{technology}",
    r"\bmust\s+have\s+(?:experience|proficiency|expertise|skills?)\s+(?:with|in|using)\s+{technology}",
    r"\b{years}(?:['\u2019])?\s+(?:of\s+)?(?:[\w-]+\s+){0,5}{technology}",
    r"{technology}(?:\s+[\w/-]+){0,4}\s*\(?{years}\)?",
    r"{technology}\s+as\s+(?:a|the)\s+primary\s+language\b",
)

# These lists always describe alternatives. One unblocked item keeps the job.
ALTERNATIVE_TECHNOLOGY_LIST_TEMPLATES = (
    r"\bproficiency\s+in\s+programming\s+languages?\s+such\s+as\s+{technologies}",
)

# Other lists are AND requirements unless their captured value uses OR.
TECHNOLOGY_LIST_TEMPLATES = (
    r"\badvanced\s+(?=[^.;:\r\n]{1,100}(?:,|\band/or\b|\band\b|\bor\b|/)){technologies}",
    r"\bexperience\s+with\s+(?=[^.;:\r\n]{1,100}(?:,|\band/or\b|\band\b)){technologies}",
    r"\bextensive\s+experience\s+(?:with|in|using)\s+{technologies}",
    r"\bexpertise\s+(?:with|in)\b[^.;:\r\n]{0,100}\bincluding\s+{technologies}",
    r"\bexpertise\s+in\s+(?=[^.;:\r\n]{1,100}(?:,|\band/or\b|\band\b|\bor\b|/)){technologies}",
    r"\bfluency\s+in\s+{technologies}",
    r"\bproficiency\s+(?:with|in)\s+{technologies}",
    r"\bproven\s+experience\s+(?:with|in|using)\s+{technologies}",
    r"\bstrong\s+(?:commercial\s+)?experience\s+(?:with|in|using)\s+{technologies}",
    r"\bstrong\s+proficiency\s+(?:with|in|using)\s+{technologies}",
    r"\bstrong\s+(?:[\w-]+\s+)*engineering\s+experience\s+(?:with|in|using)\s+{technologies}",
    r"\bhands[-\s]on\s+experience\s+(?:around\s+)?{years}\s+(?:with|in|using)\s+{technologies}",
    r"\bsignificant\s+experience\s+(?:with|in|using)\s+{technologies}",
    r"\b{years}\s+(?:досвіду\s+)?(?:[\w-]+\s+){0,4}(?:з\s+)?використанням\s+{technologies}",
    r"\bвпевнен(?:е|і)\s+знання\s+{technologies}",
    r"\bstrong\s+(?=[^,.;:\r\n]{1,40},){technologies}",
)

OPTIONAL_SIGNALS = (
    r"\b(?:0|1|zero|one)(?:\s*\+|\s*(?:-|\u2013|\u2014|to)\s*(?:\d+|two|three|four|five|six|seven|eight|nine|ten))?\s+(?:years?|рік|роки|років)\b",
    r"\bnice\s+to\s+have\b",
    r"\boptional\b",
    r"\bis\s+a\s+(?:big\s+)?plus\b",
    r"\bwould\s+be\s+a\s+(?:big\s+)?plus\b",
    r"\bnot\s+required\b",
    r"\b(?:is|are)\s+not\s+(?:strictly\s+)?required\b",
    r"\b(?:isn|aren)(?:'|\u2019)t\s+(?:strictly\s+)?required\b",
    r"\bpreferred\b",
)

# These phrases contain optional wording but qualify a version, not the
# surrounding technology requirement.
OPTIONAL_SIGNAL_EXCLUSIONS = (
    r"\([^\)\r\n]*\b\d+(?:\.\d+)*(?:\+|\s+or\s+(?:later|newer|higher))?\s+preferred\s*\)",
)

OPTIONAL_TECHNOLOGY_TEMPLATES = (
    r"{technology}\s+(?:programming\s+|development\s+)?skills?\s*\(\s*or\s+(?:another|other)\s+(?:(?:general[-\s]purpose)\s+)?programming\s+language\b",
    r"\bideally(?:\s+(?:in|with|using))?\s+{technology}",
    r"\bpreferably\s+{technology}",
    r"\bpreferences?\s+(?:toward|towards|for)\s+{technology}",
    r"\bwillingness\s+to\b[^.;:\r\n]{0,100}{technology}",
)
