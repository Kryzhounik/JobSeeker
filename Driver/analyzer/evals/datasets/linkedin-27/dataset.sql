INSERT INTO collection_runs (run_id, status, collection_status) VALUES ('eval-linkedin-27', 'pending', 'complete');
INSERT OR IGNORE INTO companies(name) VALUES ('R Systems');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4490395096','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4490395096'),'R Systems

Senior Java Backend Engineer

Moldova · 1 hour ago · 0 people clicked apply

Responses managed off LinkedIn

Remote

Internship

Get personalized tips to stand out to hirers

Find jobs where you’re a top applicant and tailor your resume with the help of Premium.

## About the job

R Systems is looking for a Senior Java Engineer who enjoys turning complex requirements into reliable, scalable backend solutions.

You’ll work with Java and Spring Boot, collaborate on API and system design, and help maintain code quality through testing, code reviews, and shared technical expertise.

Your Contribution

- Design and implement high-performance, scalable backend systems and applications.

- Collaborate with front-end developers to integrate user-facing elements with server-side logic.

- Develop and maintain APIs and web services to support application functionality.

- Ensure the performance, quality, and responsiveness of applications.

- Identify and resolve bottlenecks and bugs to improve application efficiency.

- Work with database technologies to manage, store, and retrieve data effectively.

- Participate in code reviews and contribute to team knowledge sharing.

- Stay up-to-date with emerging technologies and industry trends to implement best practices and innovative solutions.

Bring Along

- Proven experience as a backend developer or related role.

- Proficiency in Java programming with Spring Boot.

- Strong understanding of database management systems, such as MySQL, PostgreSQL, or MongoDB.

- Familiarity with RESTful APIs, microservices architecture, and cloud technologies.

Preferred Qualifications

- Previous experience with AWS and MS Azure.

- Bachelor’s degree in Computer Science, Information Technology, or a related field.

- Excellent problem-solving skills and ability to work independently or as part of a team.

- Strong communication skills to effectively collaborate with other team members and stakeholders.

If this sounds like the kind of work you enjoy, we’d love to hear from you.

The working schedule is 8 hours per day, Monday to Friday, and is carried out in a hybrid regime, involving presence in the office for 2 out of the 5 working days. A different number of days of presence in the office may be required in the future, depending on local or global policies.

What''s In It For You

Hybrid work policy

A hybrid work policy, to promote and improve work-life balance! We’ll also provide the proper work equipment to ensure there is no digital barrier you can’t break.

Health coverage

Stay healthy and feel always in safe hands with full access to private medical subscription.

Professional development

Keep your knowledge fresh and achieve your professional goals, with free and unlimited access to Udemy online learning platform.

Building a community

If you know some awesome & talented innovators, recommend them to us and you’re in for a neat referral bonus. We also hope you’ll genuinely like us enough to stick around and enjoy our long-term contribution rewards.

Click Here

People-centered offices

Our offices are not only hip and cool, with private and co-working spaces, but also situated in central city locations, so that you can get there easily. Coming to the office is also a great opportunity to enjoy some fun activities in the company of your colleagues.

Click Here

Food for thought

To feed both your body and brain, we like to organize occasional Lunch & Learn sessions. If these leave you hungry for more, you can always enroll in our mentoring & coaching sessions.

Click Here

Healthy rivalry

Running shoes are not included in your welcome kit, but you may need to get a pair ASAP because we take our (inter)company sports competitions very seriously. And if you''re competitive but sports are not your forte, you can always join our hackathons or reading marathons.

Click Here

A chance to give back

If you’re a charitable soul like us you’ll enjoy planting trees, baking cakes, playing chess, crafting, or donating to a cause in our social community events.

Click Here

Lots of fun

Our awesome people may be spread across many countries & cities, but we like to team up for some fun as often as we can. Be it team buildings, parties, or other team events, we take on every opportunity to meet and strengthen our sense of community.

Click Here
','https://www.linkedin.com/jobs/view/4490395096/','Senior Java Backend Engineer',(SELECT id FROM companies WHERE name='R Systems'),'Moldova (Remote)','remote','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('EPAM Systems');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4489849990','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4489849990'),'EPAM Systems

Senior AI-Native Software Engineer

Ukraine · 5 days ago · 2 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

Get personalized tips to stand out to hirers

Find jobs where you’re a top applicant and tailor your resume with the help of Premium.

## About the job

We''re looking for Senior Software Engineers to join small AI-native teams where AI coding agents are the default way to build software, whether that''s web applications, APIs, data pipelines, modernization solutions, or agentic systems for clients.

This role is about engineering with AI agents, not just building them. You''ll use AI coding agents as an integral part of your daily development workflow, contribute to technical decisions, ensure production-quality delivery, and collaborate directly with client stakeholders.

Kindly note that this role supports remote work, but only from within Ukraine.

Responsibilities

- Design, develop, and deliver software solutions using AI coding agents across the entire development lifecycle, from problem analysis and specifications to production

- Apply agentic SDLC practices, including spec-driven development, agent configuration, parallel and background agent execution, code review, and quality gates

- Configure and optimize repositories for AI coding agents using repository instructions, prompt files, skills, custom agents, subagents, and MCP servers

- Design and implement software architecture, ensuring solutions are scalable, maintainable, secure, and suitable for AI-assisted development

- Ensure the quality of AI-generated code through TDD, automated testing, code reviews, and static and security analysis in CI/CD pipelines

- Identify and mitigate AI agent security risks, including permissions management, secrets protection, client data confidentiality, and prompt injection

- Optimize AI usage and development efficiency by selecting appropriate models, monitoring token consumption and premium requests, and evaluating productivity impact

- Collaborate with clients and technical stakeholders to understand requirements, propose solutions, and contribute to technical discussions

- Mentor less experienced engineers and share best practices in AI-native software engineering

- Contribute to technical PoCs, solution estimates, and presales activities

Requirements

- 5+ years of professional software engineering experience, with hands-on expertise in Java, .NET, or Python, and strong software design and debugging skills

- 6+ months of daily, hands-on experience using AI coding agents (GitHub Copilot agent mode / CLI / coding agent, Claude Code, or Codex) on real-world projects

- Strong understanding of context engineering for coding agents, including repository instructions, prompt files, skills, custom agents, subagents, and MCP

- Practical experience with spec-driven development, including breaking down complex requirements into well-defined tasks suitable for AI agents

- Proven ability to maintain production-quality standards for AI-generated code through TDD, test automation, reviewing large AI-generated diffs, and CI/CD quality gates

- Understanding of AI agent security risks, including permissions, secrets management, data leakage, and prompt injection through repositories and tools

- Practical knowledge of AI cost optimization, including model selection, token and premium-request consumption, and measuring productivity impact

- Solid software architecture and system design skills

- Experience collaborating with clients and technical stakeholders, communicating technical decisions, and contributing to engineering best practices

- English B2+

Nice to have

- Building LLM or agentic applications (agent frameworks, RAG, tool calling, evals, guardrails, LLM observability)

- Rolling out AI-assisted development to other teams (enablement, playbooks, metrics)

- Cloud and AI certifications (Azure, AWS, GCP, GitHub Copilot, Anthropic)

- Business analysis mindset; consulting or presales experience; UX/UI; financial services domain

We offer

- With us you can:

- Work on a flexible schedule remotely or from any of our comfortable offices or coworking spaces in Ukraine

- Receive the necessary equipment to perform your work tasks

- Change projects and technology stacks within EPAM

- Gain experience in various business domains (Insurance, E-commerce, Healthcare, Finance, Travelling, Media, Artificial Intelligence, and more)

- Relocation opportunities may be available for eligible candidates, depending on the role and openings at other EPAM locations

- Participate in volunteer, charity programs and communities (both technical and interest-based)

- We focus on your professional growth:

- You can plan your individual career path together with your manager

- Receive regular feedback from colleagues

- Improve your English for free with certified teachers (Speaking Clubs, client interview preparation courses, etc.)

- Get the opportunity to undergo free training and certification in AWS, GCP, or Azure Clouds

- Use the internal E-learn training program (18,200+ specialized training and mentoring programs)

- Access corporate accounts on LinkedIn Learning, Get Abstract and other partner resources

- Study at EPAM Solution Architecture School with the instructors who are practicing architects

- Develop as a leader, join Delivery Management, Resource Management, Leadership Essentials school and more

- Participate in internal communities (500+ meetups, technical discussions, brainstorming sessions, online events and conferences annually)

- What we offer:

- Vacation and sick leave (including a sick leave without a medical certificate)

- A wide range of Voluntary Medical Insurance programs providing both medical treatment and various preventive options (including sports activities)

- Medical insurance for family members at corporate rates

- Company support during significant life events (childbirth or adoption, marriage, etc.)

- Support for psychological comfort: discounts on services from mental health specialists or coaches, thematic training

- E-kids program - a free programming language training program for EPAMers'' children

Kindly be advised that the set of benefits, including learning, certification, and other opportunities, may vary depending on the role you apply for. Our recruiter will be able to share more details about the specific opportunity during your general interview.

EPAM strives to provide its global team of over 62,350 professionals in more than 55 countries with opportunities for professional growth from day one of collaboration. Our colleagues are the source of EPAM''s success, so we value cooperation, strive to always understand our clients'' business and aim for the highest quality standards. No matter where you are, you will join a dedicated, diverse community that will help you realize your potential to the fullest.

---

Benefits found in job post

Medical insurance
','https://www.linkedin.com/jobs/view/4489849990/','Senior AI-Native Software Engineer',(SELECT id FROM companies WHERE name='EPAM Systems'),'Ukraine (Remote)','remote','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Wix');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4491297601','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4491297601'),'Wix

xEngineer (Backend Core)

Kyiv, Ukraine · 32 minutes ago · 3 applicants

No response insights available yet

Hybrid

Full-time

Easy Apply

Get personalized tips to stand out to hirers

Find jobs where you’re a top applicant and tailor your resume with the help of Premium.

## About the job

Job Description

As a backend-oriented xEngineer in the Account team at Wix Payments, you''ll own the services behind the payment products that businesses running on Wix rely on. You''ll deliver features end to end, from shaping what gets built to running it in production, with your focus on the backend and hands-on contributions across the stack, frontend included.

In your day-to-day, you will:

- Build and run the services behind the Wix Payments account: how a merchant signs up, gets verified and starts accepting payments

- Own the onboarding funnel end to end, from the first click on "Accept payments" to the first successful payout, and remove the steps where merchants drop off

- Model the account lifecycle and its statuses (pending, verified, restricted, suspended and the transitions between them) as clear, reliable APIs that other Wix products depend on

- Integrate with verification, risk and compliance providers, and handle their asynchronous results, retries and failures

- Make account status and required actions clear to merchants, building the backend for it and contributing to the frontend so nobody gets stuck without knowing why

- Measure the funnel with data and experiments, and ship changes that raise activation

- Own your services in production: monitoring, alerting, incident investigation and performance

- Use AI-powered tools across planning, design, implementation, testing and debugging

Qualifications

- 3+ years as a software engineer, with real ownership of backend systems running in production, preferably in JVM-based languages

- Deep backend expertise, and the drive to work outside it, including contributing to the frontend (TypeScript, React) when a feature needs it

- A track record of delivering features end to end, from shaping what gets built to running it in production

- Solid design skills: APIs, data models and service flows that scale with the product, in a microservice architecture

- Ability to debug and investigate production issues, and familiarity with CI/CD practices

- Experience working with AI tools, and good judgment about what they produce

- Curiosity about the whole system, from how a service behaves under load to how the screen in front of the merchant feels

- Care about correctness in systems that handle people''s money

- Good communication and collaboration skills

- Payments experience - an advantage

Additional Information

We''re the Wix Payments team. We give Wix users a built-in way to collect payments from their customers and manage their income online, in person and on the go. Our products cover payment acceptance, payouts, refunds and chargebacks, and they are available to merchants in about 15 countries. In payments, a bug is someone''s money, so we care about correctness as much as speed.

You''ll also be part of the Wix Engineering Guild, a community of AI-native xEngineers with deep expertise in backend, frontend or mobile, who take ownership of projects end to end.

The Account team owns the merchant''s Wix Payments account: onboarding, verification, account status and the funnel that takes a business from sign-up to its first payment.
','https://www.linkedin.com/jobs/view/4491297601/','xEngineer (Backend Core)',(SELECT id FROM companies WHERE name='Wix'),'Kyiv, Ukraine (Hybrid)','hybrid','',1);
INSERT OR IGNORE INTO companies(name) VALUES ('ALLSTARSIT');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4491795907','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4491795907'),'ALLSTARSIT

Java Software Engineer

Ukraine · 1 day ago · 72 applicants

No response insights available yet

Remote

Full-time

Easy Apply

Get personalized tips to stand out to hirers

Find jobs where you’re a top applicant and tailor your resume with the help of Premium.

## About the job

About the role

We are looking for a Java Software Engineer to join an international product company developing enterprise-grade network security solutions used by thousands of organizations worldwide.

You will become part of the Security & Compliance engineering team, working on a complex core product and building scalable backend services using modern technologies.

Responsibilities

- Design, develop, and maintain scalable Java applications

- Participate in the full software development lifecycle, from design to deployment

- Build and maintain REST and GraphQL APIs

- Develop microservices and distributed backend solutions

- Work with NoSQL databases such as MongoDB and Neo4j

- Participate in architecture discussions and code reviews

- Troubleshoot production issues and optimize application performance

- Collaborate closely with international cross-functional Agile teams

- Continuously improve code quality and engineering best practices

Requirements

- 3+ years of commercial Java development experience

- 2+ years of hands-on experience with Spring Boot

- Experience building microservices

- Experience working in Linux environments

- Experience with NoSQL databases (MongoDB, Neo4j or similar)

- Basic knowledge of Docker and Kubernetes

- Understanding of data modeling and scalable architectures

- Experience working with Agile/Scrum

- Strong problem-solving skills

- Upper-Intermediate English or higher

Nice to have

- Kotlin experience (or willingness to learn)

- Kafka or other messaging systems

- Reactive Programming

- GraphQL

- CI/CD experience

- Networking or cybersecurity background

- Angular knowledge

---

Requirements added by the job poster

• 3+ years of work experience with Java

• 3+ years of work experience with Spring Boot
','https://www.linkedin.com/jobs/view/4491795907/','Java Software Engineer',(SELECT id FROM companies WHERE name='ALLSTARSIT'),'Ukraine (Remote)','remote','',1);
INSERT OR IGNORE INTO companies(name) VALUES ('maib');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4489722492','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4489722492'),'maib

Head of Integrations

Chișinău, Moldova · 2 hours ago · 2 applicants

Company review time is typically 1 week

Hybrid

Full-time

Easy Apply

Get personalized tips to stand out to hirers

Find jobs where you’re a top applicant and tailor your resume with the help of Premium.

## About the job

The Head of Integrations role is an opportunity to turn a technical team into a real engine of digital delivery for one of the most important financial institutions in Moldova. You will lead a multidisciplinary team of .NET and Java developers and system analysts, with a clear mission: restore processes, rebuild team motivation and become a true partner to the business.This is not a pure management role. We are looking for a leader who understands code, can dive into technical details when the situation calls for it, and earns the team’s respect through competence, not title.

Responsibilities:

- In this role, you will: Define and implement improvements to the intake and processing of business tickets (prioritization, estimation, tracking);

- Introduce or optimize agile methodologies tailored to the team’s context;

- Ensure on-time, high-quality delivery, with clear escalation and reporting mechanisms;

- Define coding, code review and testing standards for the .NET and Java teams;

- Actively participate in code reviews and architectural decisions;

- Step in technically in critical situations: debugging, solution design and direct support for the team;

- Assess and propose improvements to the integration architecture (APIs, microservices, middleware);

- Act as the main point of contact between the integration team and business stakeholders;

- Translate business requirements into clear, realistic technical specifications for the team;

- Manage expectations proactively, communicating risks, blockers and scope changes early;

- Build a relationship of partnership and trust with Product Owners and other teams;

- Set clear expectations for the team, provide continuous feedback and create a safe environment;

- Build a culture of accountability and technical ownership;

- Manage individual performance, including difficult conversations where needed.

Qualifications:

- Skills and competencies that make you a good fit:At least 3 years in tech lead, engineering manager or similar roles, with direct reports;

- Proven ability to motivate teams going through difficult times;

- Experience implementing or optimizing processes;

- Clear communication adapted to the audience, with both developers and senior management;

- Integrity and straightforwardness: you say what you think and deliver what you promise;

- At least 7 years of software development experience, with solid expertise in .NET (C#) and/or Java;

- Demonstrated experience in system integrations: REST APIs, SOAP, messaging (Kafka, RabbitMQ), ESB;

- Knowledge of microservices, distributed architectures and design patterns;

- Familiarity with relational databases (SQL Server, Oracle, PostgreSQL) and CI/CD practices;

- Experience with cloud platforms (Azure, AWS) and containerization (Docker, Kubernetes).

Benefits:

- At maib, you will enjoy: A professional, innovative work environment that sets trends in banking and beyond, and people who will inspire you at an agile pace;

- A modern, centrally located office with coffee, relaxation areas, a gym, kitchen, terrace and more;

- Meal vouchers;

- Mortgage loans and other banking services on preferential terms;

- Private health insurance or a gym membership;

- Flexibility to work hybrid;

- Professional and personal development opportunities, and team-building events;

- A wide range of discounts at maib partners.

Send your CV to resurseumane@maib.md, stating the position you are applying for, and let’s excel together.

This position is for local people of Moldova.

---

Benefits found in job post

Medical insurance
','https://www.linkedin.com/jobs/view/4489722492/','Head of Integrations',(SELECT id FROM companies WHERE name='maib'),'Chișinău, Moldova (Hybrid)','hybrid','',1);
INSERT OR IGNORE INTO companies(name) VALUES ('Cast AI');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4490301710','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4490301710'),'Cast AI

Senior Software Engineer | AI Agents & Cloud Platform (Cast Pilot team)

Moldova · 4 days ago · 9 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

Get personalized tips to stand out to hirers

Find jobs where you’re a top applicant and tailor your resume with the help of Premium.

## About the job

What You''ll Be Solving

A platform engineer opens their AI client of choice, connects it to CAST AI, and types: "Why did our spend jump 40% in eu-west last week?" Thirty seconds later they have an answer, pulled from live cluster state and cost data, with links back to the evidence. No dashboards. No YAML archaeology. Just a question and a correct, permission-checked answer.

Your job is to make that interaction boring. Reliably, securely, at production scale.

Behind it sits the hard part: an AI assistant that reasons over thousands of production Kubernetes clusters, a platform that safely exposes company infrastructure to external AI clients, and quality pipelines that tell us when the agent is wrong before a customer does. Get it right, and operators stop clicking through consoles entirely. Get it wrong once, and an agent gives a confidently wrong answer to someone managing production.

CAST AI already runs Kubernetes infrastructure autonomously. Our automation makes thousands of cost-and-reliability decisions a day. Cast Pilot is the team building the intelligence layer on top of that: AI assistants for Kubernetes and the cloud itself. Our assistant already answers thousands of questions a month from hundreds of organizations. Now we''re opening the platform up: a public, permission-aware MCP server so customers can plug CAST AI straight into their own AI clients and agents, deeper connectivity across all three major clouds (AWS, GCP, Azure), and a continuous push to make our agents smarter, better grounded, and more capable.

This is greenfield AI platform work on top of battle-tested infrastructure. Most features ship in 1 to 4 weeks.

This is a location-specific opportunity. We are currently accepting applications from candidates residing in the following European countries: Bulgaria, Croatia, Estonia, Greece, Hungary, Latvia, Lithuania, Poland, Romania, Slovakia, Cyprus, Malta, Finland, Sweden, Ireland, Moldova, North Macedonia, Montenegro, Slovenia, and Ukraine.

Requirements

- Strong software engineering fundamentals: you design clean abstractions, write maintainable code, and can justify your architecture choices, not just make something work. You''re a generalist who is as comfortable debugging a distributed system as designing an auth flow or tuning an agent''s behavior.

- Production experience with Go is strongly preferred; candidates without Go should demonstrate strong systems programming skills in a comparable language.

- Experience building or operating large-scale SaaS platforms in production: multi-tenant systems, high volumes, and the failure modes that only show up at scale.

- Solid understanding of Kubernetes. Our entire platform runs on it, and the assistants we build answer questions about it. You should be at home with autoscaling, scheduling, and networking concepts.

- You''ve personally driven a complex project end-to-end.

- Hands-on experience with AI coding agents and agentic development workflows. This is how we build, not just what we build.

- You''ve used observability tooling in production.

- CI/CD and DevOps practices experience.

- Strong English skills, both verbal and written.

- Startup mindset: adaptable, proactive, and comfortable with ambiguity.

What will make you stand out

- You''ve built agentic systems that shipped: MCP servers or clients, agent gateways, tool-use orchestration, RAG pipelines, evaluation frameworks, and you can talk about what broke in production and how you found out. This is the single biggest differentiator for our team.

- Deep hands-on experience with one or more of AWS, GCP, or Azure, with real understanding of IAM, compute, networking, and their APIs, not just console-level usage.

- Experience with LLM application quality: evaluations, prompt engineering at scale, latency and cost optimization of agent loops.

- Knowledge of OAuth 2.0 / OIDC and multi-tenant authorization design.

- Deep knowledge of EKS, GKE, or AKS internals.

- Kubernetes or cloud-native OSS contributions.

Responsibilities

- Design and build the systems behind our AI assistant: agent flows, context retrieval, and the data connections that let it reason about live Kubernetes infrastructure.

- Build and evolve our public, permission-aware MCP server: curated tools, identity, audit, and strict safety gates, so customers can use CAST AI from their own AI clients.

- Develop and maintain cloud connectivity across AWS, GCP, and Azure, and the data foundations our agents build on.

- Write production Go services that interact with cloud provider APIs, the Kubernetes control plane, and LLM APIs in real time.

- Own features end-to-end: from design through implementation, testing, and production rollout (most projects ship in 1 to 4 weeks).

- Debug novel problems: an agent giving a wrong answer, a cross-cloud permission edge case, a data freshness issue that only appears at scale.

- Build the quality harness: evals, telemetry, feedback loops, that let us ship agent improvements without regressions.

- Contribute to product direction and backlog. Shape what gets built next, not just how it''s built.

- Participate in the team''s on-call rotation.

Tools We Use Daily

- Programming Languages: Go

- AI & Agents: MCP (servers and clients), agent gateways, LLM provider APIs, RAG, evaluation pipelines

- Cloud & Orchestration: Kubernetes, AWS, GCP, Azure

- Infrastructure as Code: Terraform

- Databases & Storage: PostgreSQL, Cloud Object Storage, ClickHouse

- Workflows & APIs: Temporal, GCP Pub/Sub, gRPC, REST

- Observability & Product Analytics: Prometheus, Grafana, Loki, Tempo, PostHog

- CI/CD & GitOps: GitLab/GitHub CI with ArgoCD and Kargo

What''s in it for you?

- Competitive salary (€6,500 to €9,000 gross, depending on the level of experience)

- Work on genuinely new problems: production MCP platforms and AI assistants for Kubernetes and cloud infrastructure are not solved domains. You''ll be defining them, not following a playbook.

- Equity options.

- Real users, fast: your work ships to hundreds of organizations in weeks, not quarters.

- Enjoy a flexible, remote-first global environment.

- Collaborate with a global team of cloud experts and innovators, passionate about pushing the boundaries of Kubernetes technology.

- Spend 10% of your work time on personal projects or self-improvement.

- Learning budget for professional and personal development, including access to international conferences and courses.

- Annual hackathon to spark new ideas and strengthen team bonds.

- Team-building budget and company events to connect with your colleagues.

- Equipment budget to ensure you have everything you need.

- Extra days off to help maintain a healthy work-life balance.

Hiring process

- Screening call with Recruiter

- Hiring Manager interview

- Technical interview (system design)

- Live coding

- Culture Check interview with an executive

As part of our standard hiring process, a background check may be conducted at the final stage of recruitment through our third-party provider, Checkr. Please note that Cast AI does not provide any form of visa sponsorship/work permit.
','https://www.linkedin.com/jobs/view/4490301710/','Senior Software Engineer | AI Agents & Cloud Platform (Cast Pilot team)',(SELECT id FROM companies WHERE name='Cast AI'),'Moldova (Remote)','remote','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('JiBe ERP');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4489937521','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4489937521'),'JiBe ERP

Senior Agentic AI Builder

Ukraine · 4 days ago · 59 applicants

Actively reviewing applicants

Remote

Full-time

Easy Apply

Get personalized tips to stand out to hirers

Find jobs where you’re a top applicant and tailor your resume with the help of Premium.

## About the job

About JiBe ERP

At JiBe ERP, we are pioneering the next generation of enterprise software. We are looking for an exceptional, high-impact Senior Agentic AI Builder to take full ownership of building a complete, end-to-end AI module from scratch. You will drive the architecture, model integrations, and autonomous workflows that bring next-level intelligence to our ERP platform.

What You Will Do:

- End-to-End Ownership: Lead the complete design, architecture, and development of a brand-new AI module from concept to production.

- Agentic Development: Build autonomous workflows, advanced AI agents, and smart reasoning loops capable of executing complex multi-step tasks independently.

- Cutting-Edge Integration: Implement and orchestrate state-of-the-art LLMs and foundational models to deliver tangible business value.

- AI-Accelerated Workflow: Leverage advanced developer AI tools daily, including Claude Code and Cursor, to maximize engineering velocity and code quality.

Requirements:

- Proven AI & Software Engineering Experience: Strong background in building complex software systems with a deep specialization in Machine Learning, LLMs, and modern AI engineering.

- Expertise in Agentic AI: Hands-on experience developing agentic workflows and multi-agent systems.

- Advanced AI Tool Proficiency: Mastery of modern AI coding assistants like Claude Code and Cursor (Mandatory).

- High Ownership & Leadership: Track record of driving modules or products independently from scratch in fast-paced environments.

- Enterprise Background (Bonus): Experience working with ERP systems or complex enterprise-grade platforms is a significant plus.
','https://www.linkedin.com/jobs/view/4489937521/','Senior Agentic AI Builder',(SELECT id FROM companies WHERE name='JiBe ERP'),'Ukraine (Remote)','remote','',1);
INSERT OR IGNORE INTO companies(name) VALUES ('EPAM Systems');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4489855612','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4489855612'),'EPAM Systems

AI Native Engineer

Ukraine · 5 days ago · 2 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

Get personalized tips to stand out to hirers

Find jobs where you’re a top applicant and tailor your resume with the help of Premium.

## About the job

We''re forming small AI-native teams where AI coding agents are the default way to build software, whether that''s web apps, APIs, data pipelines, modernization or agentic systems for clients. This role is about engineering with agents, not only building them. As Lead, you define how the team works with agents, own delivery quality, and work directly with client stakeholders.

Kindly note that this role supports remote work, but only from within Ukraine.

Responsibilities

- Lead technical delivery of a small AI-native team, from problem framing and specs to production

- Design and evolve the team''s agentic SDLC: spec-driven workflow, agent configuration, parallel and background agent work, review and quality gates

- Set up repos for agents: instructions, prompt files, skills, custom agents, MCP servers, plus the architecture, docs and tests that let agents work reliably

- Define how AI-generated code is checked: TDD, test automation, code review, static and security analysis in CI/CD

- Set safe-use rules for agents: permissions, secrets, client data, approved tools

- Measure and optimize AI cost and delivery impact (tokens and premium requests, model choice, cycle time, rework)

- Own the solution architecture; mentor engineers in agentic practices; contribute to PoCs, estimates and presales

Requirements

- 7+ years in software engineering, with hands-on Java, .NET or Python, and strong design and debugging skills

- 6+ months of daily, hands-on use of AI coding agents (GitHub Copilot agent mode / CLI / coding agent, Claude Code or Codex) on real projects

- Deep context engineering for coding agents: repo instructions, prompt files, skills, custom agents and subagents, MCP

- Spec-driven development in practice, including breaking work into tasks an agent can do well

- Proven practices for keeping AI-generated code at production quality: TDD, test automation, reviewing large AI diffs, CI/CD quality gates

- Understanding of agent security risks: permissions, secrets, data leakage, prompt injection through repos and tools

- Practical AI cost awareness: model selection per task, token and premium-request economics, measuring productivity impact

- Solution architecture skills; experience leading and mentoring engineers and working with clients

- English level B2+

Nice to have

- Experience building LLM or agentic applications (agent frameworks, RAG, tool calling, evals, guardrails, LLM observability)

- Showcase of rolling out AI-assisted development to other teams (enablement, playbooks, metrics)

- Cloud and AI certifications (Azure, AWS, GCP, GitHub Copilot, Anthropic)

- Business analysis mindset with consulting or presales experience

- Familiarity with UX/UI and the financial services domain

We offer

- With us you can:

- Work on a flexible schedule remotely or from any of our comfortable offices or coworking spaces in Ukraine

- Receive the necessary equipment to perform your work tasks

- Change projects and technology stacks within EPAM

- Gain experience in various business domains (Insurance, E-commerce, Healthcare, Finance, Travelling, Media, Artificial Intelligence, and more)

- Relocation opportunities may be available for eligible candidates, depending on the role and openings at other EPAM locations

- Participate in volunteer, charity programs and communities (both technical and interest-based)

- We focus on your professional growth:

- You can plan your individual career path together with your manager

- Receive regular feedback from colleagues

- Improve your English for free with certified teachers (Speaking Clubs, client interview preparation courses, etc.)

- Get the opportunity to undergo free training and certification in AWS, GCP, or Azure Clouds

- Use the internal E-learn training program (18,200+ specialized training and mentoring programs)

- Access corporate accounts on LinkedIn Learning, Get Abstract and other partner resources

- Study at EPAM Solution Architecture School with the instructors who are practicing architects

- Develop as a leader, join Delivery Management, Resource Management, Leadership Essentials school and more

- Participate in internal communities (500+ meetups, technical discussions, brainstorming sessions, online events and conferences annually)

- What we offer:

- Vacation and sick leave (including a sick leave without a medical certificate)

- A wide range of Voluntary Medical Insurance programs providing both medical treatment and various preventive options (including sports activities)

- Medical insurance for family members at corporate rates

- Company support during significant life events (childbirth or adoption, marriage, etc.)

- Support for psychological comfort: discounts on services from mental health specialists or coaches, thematic training

- E-kids program - a free programming language training program for EPAMers'' children

Kindly be advised that the set of benefits, including learning, certification, and other opportunities, may vary depending on the role you apply for. Our recruiter will be able to share more details about the specific opportunity during your general interview.

EPAM strives to provide its global team of over 62,350 professionals in more than 55 countries with opportunities for professional growth from day one of collaboration. Our colleagues are the source of EPAM''s success, so we value cooperation, strive to always understand our clients'' business and aim for the highest quality standards. No matter where you are, you will join a dedicated, diverse community that will help you realize your potential to the fullest.

---

Benefits found in job post

Medical insurance
','https://www.linkedin.com/jobs/view/4489855612/','AI Native Engineer',(SELECT id FROM companies WHERE name='EPAM Systems'),'Ukraine (Remote)','remote','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('GlobalLogic');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4483227272','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4483227272'),'GlobalLogic

Senior Java Developer – NNI – AEM & Digital Experience Platforms IRC307125

Lviv, Lviv, Ukraine · Reposted 2 days ago · 36 people clicked apply

Responses managed off LinkedIn

On-site

Full-time

Get personalized tips to stand out to hirers

Find jobs where you’re a top applicant and tailor your resume with the help of Premium.

## About the job

Description

Customer is one of the world’s leading healthcare organizations, operating across 170+ countries with a strong focus on diabetes, obesity, and rare disease care. We are in managed service contract for 55+ AEM websites.

Requirements

Required Skills:

– Strong proficiency in Java 8+, with solid hands-on experience in Spring Boot and Spring MVC.

– Experience in RESTful API design and development.

– Familiarity with SQL/NoSQL databases.

– Hands-on experience with Git, Maven/Gradle, and CI/CD pipelines.

– Experience working with API documentation tools like Swagger/OpenAPI.

– Knowledge of .NET.

Preferred Skills:

– Experience with Azure DevOps (ADO) or GitHub Actions.

Soft Skills:

– Strong analytical and problem-solving abilities.

– Excellent communication and teamwork skills.

– Ability to work independently and manage migration-specific tasks with minimal guidance.

Job responsibilities

Support and enhancement for 11 Java services – Microservice, Spring boot

What we offer

Empowering Projects: With 500+ clients spanning diverse industries and domains, we provide an exciting opportunity to contribute to groundbreaking projects that leverage cutting-edge technologies. As a team, we engineer digital products that positively impact people’s lives.

Empowering Growth: We foster a culture of continuous learning and professional development. Our dedication is to provide timely and comprehensive assistance for every consultant through our dedicated Learning & Development team, ensuring their continuous growth and success.

DE&I Matters: At GlobalLogic, we deeply value and embrace diversity. We are dedicated to providing equal opportunities for all individuals, fostering an inclusive and empowering work environment.

Career Development: Our corporate culture places a strong emphasis on career development, offering abundant opportunities for growth. Regular interactions with our teams ensure their engagement, motivation, and recognition. We empower our team members to pursue their career goals with confidence and enthusiasm.

Comprehensive Benefits: In addition to equitable compensation, we provide a comprehensive benefits package that prioritizes the overall well-being of our consultants. We genuinely care about their health and strive to create a positive work environment.

Flexible Opportunities: At GlobalLogic, we prioritize work-life balance by offering flexible opportunities tailored to your lifestyle. Explore relocation and rotation options for diverse cultural and professional experiences in different countries with our company.

About GlobalLogic

GlobalLogic, a Hitachi Group Company, is a trusted digital engineering partner to the world’s largest and most forward-thinking companies. Since 2000, we’ve been at the forefront of the digital revolution – helping create some of the most innovative and widely used digital products and experiences. Today we continue to collaborate with clients in transforming businesses and redefining industries through intelligent products, platforms, and services.
','https://www.linkedin.com/jobs/view/4483227272/','Senior Java Developer – NNI – AEM & Digital Experience Platforms IRC307125',(SELECT id FROM companies WHERE name='GlobalLogic'),'Lviv','unknown','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Relativity');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4491778642','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4491778642'),'Relativity

Software Engineer

Georgia · 1 day ago · 74 people clicked apply

Responses managed off LinkedIn

Hybrid

Full-time

Get personalized tips to stand out to hirers

Find jobs where you’re a top applicant and tailor your resume with the help of Premium.

## About the job

Posting Type

Hybrid/Remote

Job Overview

WHO WE ARE

Relativity is a leading legal data intelligence company building technology that helps users organize data, discover the truth, and act on it with confidence. Our AI-powered, cloud platform, RelativityOne, transforms massive volumes of complex information into actionable insights for litigation, investigations, regulatory inquiries, data breach responses, and other high-stakes legal work where accuracy and trust are crucial.

The world’s largest law firms, corporations, and government agencies rely on Relativity’s legal AI software to securely surface and manage the most relevant and impactful information in their matters. Beyond our commercial impact, we’re deeply committed to expanding access to technology for academic institutions through Relativity Academic and supporting pro bono legal work through Justice for Change.

What We Do

At Relativity, engineers don’t just write code. They build the systems that power AI-driven workflows at massive scale, enabling customers to uncover insights and act on complex data with confidence.

aiR Services Team

The aiR Services team builds the shared backend platform that powers Relativity’s generative AI products. These systems enable secure and scalable access to foundation models across the aiR Suite while handling challenges such as limited model capacity, global data privacy requirements, and fair usage across products and customers. The team works on high-throughput, distributed systems that route AI traffic across regions and providers, protect downstream services from reliability risks, and optimize cost and performance at scale.

If you’re excited about building platforms that power an entire product suite, working with generative AI systems in production, and solving distributed systems challenges at global scale, this is a high-impact team to join.

Job Description And Requirements

ABOUT THE ROLE

As a Software Engineer, you will build and operate backend services that power Relativity’s generative AI platform, aiR. You will work at the intersection of distributed systems, cloud infrastructure, and applied AI, helping to deliver reliable, scalable systems that sit on the critical path of AI-powered workflows.

This role reports to the Manager of Software Engineering and provides the opportunity to work on multi-region, multi-cloud systems that handle large-scale AI traffic and complex orchestration challenges.

What You’ll Do

- Design and build performant, scalable, and secure backend services with a strong focus on quality, not just meeting requirements

- Collaborate with a software development team to deliver high-quality, reliable systems that operate at massive scale

- Develop and maintain systems that support high-throughput AI workloads, including asynchronous job orchestration and distributed processing

- Write comprehensive unit and integration tests supported by static analysis and thoughtful test strategy development

- Contribute to improving engineering processes by recommending enhancements to tools, workflows, and practices

- Participate in pair programming to improve software quality and share design and implementation knowledge

- Contribute to building systems that route and manage AI traffic efficiently while maintaining reliability and fairness across workloads

Required

WHAT WE’RE LOOKING FOR

- Bachelor’s degree in Computer Science or a related field, or equivalent practical experience

- Experience building backend services using languages such as C#, Python, or similar

- Understanding of software engineering disciplines and the ability to work across multiple layers of the application stack

- Strong foundational knowledge of distributed systems, scalability, and backend service design

Preferred

- Understanding of DevOps principles and experience with tools such as GitHub Actions

- Experience working with large language model (LLM) APIs or generative AI systems

- Experience designing and building scalable systems in Azure or other cloud platforms

- Experience with Kubernetes, serverless technologies, and cloud-native architectures

- Familiarity with domain-driven design and event-driven architecture

- Experience with AI-driven development practices or AI-assisted coding tools

- Awareness of emerging technology trends and their practical application in engineering

WHY WE COULD BE A GREAT FIT

Impactful AI Platform

- Build foundational systems that power Relativity’s generative AI capabilities and enable products across the aiR Suite.

Distributed Systems at Scale

- Work on high-throughput, multi-region services that handle large volumes of AI traffic reliably and efficiently.

Cutting-Edge Technology

- Gain hands-on experience with generative AI systems, cloud-native infrastructure, and modern distributed architectures.

Growth Opportunity

- Develop deep expertise in backend systems, cloud platforms, and AI-driven services while working on highly visible, impactful projects.

Collaborative Culture

- Work with a team that values strong engineering fundamentals, shared ownership, and continuous learning.

Compensation And Benefits

- Competitive salary, health and retirement programs, wellness resources, discretionary time off (DTO), parental leave for primary and secondary caregivers, company-wide breaks, home office stipend, and an equity program.

Relativity is committed to competitive, fair, and equitable compensation practices.

This position is eligible for total compensation which includes a competitive base salary, an annual performance bonus, and long-term incentives.

The expected salary range for this role is between following values:

$79,000 and $119,000

The final offered salary will be based on several factors, including but not limited to the candidate''s depth of experience, skill set, qualifications, and internal pay equity. Hiring at the top end of the range would not be typical, to allow for future meaningful salary growth in this position.

Required Skills

Automation, Documentations, Engineering Design, Problem Solving, Process Improvements, Project Management, Quality Assurance (QA), Research and Development, System Integration, Troubleshooting
','https://www.linkedin.com/jobs/view/4491778642/','Software Engineer',(SELECT id FROM companies WHERE name='Relativity'),'Georgia (Hybrid)','hybrid','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Ajaib');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4489100157','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4489100157'),'Ajaib

Senior Backend Engineer II - Platform

Poland · 1 week ago · Over 100 applicants

No response insights available yet

Remote

Full-time

Easy Apply

## About the job

Founded to transform retail finance, Ajaib Group is an Indonesian fintech unicorn breaking barriers to financial access. Having recently secured a $270M Series C—the single largest funding round in Southeast Asia over the past four years—we are backed by premier global investors including Y Combinator, Ribbit Capital, SoftBank, and SBI Holdings.

To build world-class products, we assemble world-class talent. Supported by our fully remote work arrangement, our team spans Eastern Europe, the Middle East, India, and Southeast Asia, allowing you to work with brilliant engineers and builders from around the globe from wherever you are. We operate with an impact-driven purpose where everyone is the owner of our customers'' satisfaction. Driven by a growth mindset and a fierce will to win, we refuse to settle and constantly push engineering boundaries.

Role Overview

As a Senior Backend Engineer II in the Platform team, you own shared backend services that every Ajaib product depends on, such as identity, notifications, configuration and API gateways, from design to production. You design for many users and many teams, keep services reliable and secure under real traffic, and mentor engineers around you.

- Design, build and run scalable, secure shared backend services and APIs used by many product teams

- Own larger services end to end, including monitoring, alerting, on-call follow-up and support for the teams that use them

- Design APIs that stay stable: clear contracts, versioning, backward compatibility and safe deprecation

- Build and improve identity and access features, such as sign-in, sessions, tokens, permissions and account security

- Make shared services resilient: rate limiting, graceful degradation, isolation between consumers and safe rollouts

- Improve observability with useful metrics, logs and traces, and set service level objectives with the teams that depend on you

- Lead design reviews and give thoughtful code reviews

- Investigate production incidents, run root cause analysis and ship lasting fixes

- Mentor mid-level engineers and share knowledge within the team

- Work with product engineering teams, Security, Infrastructure and Architecture to turn requirements into reliable solutions

- Design and scale centralized event streaming and asynchronous messaging platforms (Kafka) that power org-wide notifications and async workflows

- Build and manage high-throughput, low-latency API Gateways and WebSocket infrastructure that serve as the front door for all Ajaib clients

- Develop and maintain robust feature flagging solutions that allow product teams to safely deploy, canary test, and roll back features dynamically

Requirements

- Education: Bachelor''s degree in Computer Science or equivalent practical experience

- Experience: 5+ years of professional backend engineering, with a track record of owning production services used by other teams or high traffic

- Languages: Strong in Java, Go or another modern backend language

- System Design: Solid understanding of microservices, REST/gRPC APIs, event-driven design, and reliability and observability practices

- Data & Messaging: Hands-on with relational and NoSQL databases (query tuning), caching (Redis) and messaging (Kafka)

- Security: Strong knowledge of authentication and authorization (OAuth2, OpenID Connect, JWT) and secure coding

- Infrastructure: Experience running services on cloud platforms (GCP or AWS), with containers and CI/CD

- Mindset: Ownership mindset, clear communication and a service attitude toward internal users. Experience in fintech or another regulated environment is a plus

- Experience transitioning legacy monolithic auth to centralized identity providers (IdP) or implementing Zero Trust patterns across microservices is a strong plus

Benefits

- Performance Bonus - Get rewarded for the impact you make

- Private Insurance - Comprehensive health coverage for you

- Paid Time Off - Take the time you need to recharge and come back stronger

- Learning & Development - Company-sponsored training, courses, and professional certifications

- Hybrid Work Arrangement - Enjoy the flexibility of working both remotely and from the office

- Career Growth - Work closely with experienced leaders and take on meaningful ownership as we scale

- Employee Perks - Enjoy exclusive discounts, partnerships, and lifestyle perks

- Collaborative & High-Ownership Culture - Work with smart, driven people in cross-functional teams, take ownership, and make a meaningful impact for our users
','https://www.linkedin.com/jobs/view/4489100157/','Senior Backend Engineer II - Platform',(SELECT id FROM companies WHERE name='Ajaib'),'Poland','unknown','',1);
INSERT OR IGNORE INTO companies(name) VALUES ('DraftKings Inc.');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4489588677','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4489588677'),'DraftKings Inc.

Senior Backend Engineer

Ukraine · 1 week ago · Over 100 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

## About the job

At DraftKings, AI is becoming an integral part of both our present and future, powering how work gets done today, guiding smarter decisions, and sparking bold ideas. It’s transforming how we enhance customer experiences, streamline operations, and unlock new possibilities. Our teams are energized by innovation and readily embrace emerging technology. We’re not waiting for the future to arrive. We’re shaping it, one bold step at a time. To those who see AI as a driver of progress, come build the future together.

The Crown Is Yours

As a Senior Backend Engineer, you''ll build the high-scale backend systems that power real-time experiences for millions of customers. From day one, you''ll ship production-ready code, deliver features with speed and consistency, and own services end-to-end in a fast-moving, always-on environment. You''ll work alongside passionate engineers, architects, and product leaders who value quality, speed, and impact. Your ideas will shape what comes next and your growth won''t slow down.

What You''ll Do

- Design and develop scalable back-end systems and APIs that power high-impact applications used by a large, engaged user base around the clock.

- Deliver production-ready code, driving forward new features and capabilities in a 24/7 sports betting environment.

- Prioritize agility and scalability equally to support rapid product rollouts while ensuring systems are built to pivot and perform at scale.

- Own the full software development lifecycle, from initial design through long-term maintenance, performance tuning, and reliability improvements.

- Triage, investigate, and resolve production issues with urgency, contributing to a culture of continuous improvement and operational excellence.

- Ensure all components are built to withstand high volume and real-time demands, optimizing for speed and efficiency.

- Collaborate cross-functionally to align technical execution with product goals, mentoring peers and driving quality standards across the team.

What You''ll Bring

- Bachelor''s degree in Computer Science or any relevant combination of education, experience, and training.

- At least 3 years of professional experience in object-oriented backend development using languages such as C#, with deep knowledge of OOP principles, design patterns, and backend system architecture.

- Proven success designing and building scalable distributed systems in Cloud environments such as AWS or Google Cloud Platform (GCP).

- Advanced proficiency in writing and maintaining automated test suites, including unit, integration, and performance tests, for large-scale backend services.

- A demonstrated ability to mentor and grow other engineers through code reviews, design reviews, and collaborative debugging.

Join Our Team

We’re a publicly traded (NASDAQ: DKNG) technology company headquartered in Boston. As a regulated gaming company, you may be required to obtain a gaming license issued by the appropriate state agency as a condition of employment. Don’t worry, we’ll guide you through the process if this is relevant to your role.
','https://www.linkedin.com/jobs/view/4489588677/','Senior Backend Engineer',(SELECT id FROM companies WHERE name='DraftKings Inc.'),'Ukraine','unknown','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Camunda');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4476025881','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4476025881'),'Camunda

Senior Software Engineer, Backend - Data Streaming

Budapest Metropolitan Area · Reposted 2 weeks ago · 27 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

## About the job

Camunda is the enterprise platform for agentic orchestration, enabling organizations to coordinate AI agents, people, and systems across complex, end-to-end business processes. With built-in governance, auditability, and human oversight, Camunda gives enterprises the control they need to move AI from pilots to production — safely and at scale. Trusted by over 700 organizations worldwide, including 9 of top 10 US banks, Camunda helps enterprises boost operational efficiency, accelerate time-to-value, and deliver better customer experiences.

Fully remote and global, we are in the middle of something bigger: transforming into an AI-first organisation, built on our own platform. We use Agentic AI to automate, orchestrate intelligent processes, and elevate human contribution across every team.

Named GP Bullhound’s Top 100 Next Unicorn list, 2025 Great Place to Work certified. Visionary in 2025 Gartner® Magic Quadrant™ for Business Orchestration and Automation Technologies. ranked 3rd in Flexa''s 2026 Most Flexible Companies, We’re growing fast and looking for top talent to join our team. If you want meaningful work, visible impact and put something genuinely rare on your CV, keep reading.

About The Role

As a Senior Software Engineer on Camunda''s new Data Streaming team, you''ll help build a foundational new piece of architecture that changes how customers and internal products access process data. Today, customers have no first-class way to stream their own process data out of Camunda, and Camunda''s own analytics product is bottlenecked by querying the same storage the live engine depends on. You''ll be part of a greenfield team rebuilding how process data is consumed and served at scale, built on top of a durable, continuously-written streaming layer that decouples data consumption from the live engine. You''ll be part of a small team from day one, with real room to shape both the architecture and the team''s scope as it matures.

At Camunda, we approach engineering in a holistic manner. As such, you will form strong partnerships with product management, the Data Layer and Distributed Systems teams, QA, and support, and work closely with your peers to not only deliver but directly influence how our data streaming architecture evolves to meet our customers'' needs.

What You Will Do

- Help rebuild how Camunda''s analytics product consumes and serves process data at scale, moving it off the engine''s critical path entirely.

- Design and build the core streaming infrastructure that decouples process data access from the live execution engine, backed by durable, object storage.

- Build customer-facing data streaming capabilities, including a supported way to integrate with systems like Kafka, so customers can reliably get their process data into their own tools.

- Make foundational architecture decisions as part of a small, high-ownership team, including how the system guarantees consistency and durability across a distributed streaming pipeline.

- Participate in L3 support and on-call rotations, partnering with Support and SRE to investigate data layer production issues, and feeding what you learn back into the product.

Must Haves

What You Will Bring to the Role:

- 7+ years of experience as a backend engineer, in Java, with a strong focus on distributed or data-intensive systems.

- Hands-on experience building or operating an analytical serving layer over a table format such as Apache Iceberg or Delta Lake - including experience with an embedded/lightweight query engine (e.g. DuckDB, or similar), or a stateful stream-processing framework (e.g. Flink, Kafka Streams).

- A track record of working with object storage systems (S3/GCS/Azure or similar) and building reliable, idempotent upload/commit protocols against them.

- Solid grounding in the reasoning behind correctness and consistency in data-intensive systems (e.g. concurrent writes, atomicity, idempotency).

- A demonstrated track record of driving a technical initiative through genuine ambiguity in a still-being-designed system, not just extending an established one.

- Proven experience leading medium-to-large engineering initiatives (3+ months) end-to-end, from solution design, project planning, and coordinating across teams, right through to driving predictable delivery.

- Strong communication and collaboration skills in a remote-first environment: you translate requirements into actionable technical work with Product, and give and receive clear, direct feedback in code review.

Nice To Haves

- Experience with Kafka or a similar streaming/event-log system

- Experience with embedded state stores (RocksDB or similar) for projection/checkpointing workloads.

- Experience with stream processing frameworks (e.g. Apache Flink), particularly stateful processing with local state backends and checkpointing.

- Familiarity with object-storage-native streaming architectures (e.g. systems like WarpStream) is a plus, even without direct hands-on experience.

This role is an existing vacancy

#C1

Compensation

What We Have to Offer:

We offer competitive, fair, and transparent compensation. Salary ranges are location-based, with Standard and Major markets (global tech hubs) reflecting local competition.

The Annual Total Target Cash (base salary + 100% variable target, where applicable) shown below spans from the minimum in a Standard market to the maximum in a Major market. Final offers depend on skills, experience, and location, and we typically hire in the first half of the range to allow room for growth:

United States: $143,800.00 to $231,900.00

United Kingdom: £90,300.00 to £148,500.00

Singapore: S$178,600.00 to S$267,900.00

Canada: C$152,700.00 to C$251,200.00

If you’re based elsewhere, you’ll be hired via Remote.com (our global employer partner), and your Talent Acquisition Partner will provide a personalized Total Rewards Calculator after your first interview.

Equity: We also offer equity (where applicable) through our Virtual Stock Option Plan (VSOP).

Benefits & Perks

We invest in your wellbeing, growth, and ability to connect, along with perks that support you no matter where you’re based. Our benefits are globally designed and locally delivered where applicable.

- Remote & Flexible: Work from anywhere with the setup that suits you, home office budget, co-working space support, and flexible time off to recharge when you need it.

- In Person Connection: We invest in meaningful face time through our Annual Kickoff (Vienna in 2025, Madrid in 2026!), team offsites, and Camundi Connection Budgets, including contributing to meetups while travelling,, and local gatherings with fellow Camundi.

- Health & Wellbeing: Access locally tailored healthcare, Modern Health for global mental wellbeing, and our Live Well Lifestyle Spending Account (LSA), a flexible, global benefit that puts you in control of your whole life, not just work, from: staying active, to caring for family, exploring personal passions, meaningful experiences, and investing in your financial wellbeing. The Live Well program launches in 2026 and scales to €1,000 annually from 2027.

- Financial Security: Retirement and pension plans (often with company contributions), plus life and disability insurance where relevant.

- Professional Growth: Up to $/€/£1,000 per year for self-driven learning: courses, certifications, books, you decide!

AI in our hiring process: Camunda may use AI tools to aid the screening of applications and during the interview process. You can learn more here

”Everyone is welcome at Camunda” — it’s a celebrated component of our culture. We strive to create an inclusive environment that empowers our people. At Camunda, we honour diverse cultures and backgrounds and are proud to be an equal opportunity employer. All qualified applicants will receive consideration without regard to gender, race, ethnicity, religion, belief, sexual orientation, age, disability or any other protected characteristics under applicable law. We are looking forward to your application!

Come join us and be part of Camunda’s incredible journey: Make an impact at a pivotal moment in our story!

Please be aware that scammers may impersonate Camunda by reaching out about job opportunities. Camunda will only contact candidates from an email address ending in @camunda.com or @talent.camunda.com. We will never ask candidates for bank account details, checks, payment, or other sensitive financial information as part of our hiring process. If you receive a suspicious message, please do not respond, click any links, or share personal information.

Note for recruiting and placement agencies: Camunda does not accept unsolicited agency resumes. Please do not forward resumes to our careers site, employees, or any other Camunda contact unless we have specifically engaged your firm for that search. Camunda will not pay fees related to unsolicited resumes.

For information about how your personal data is processed, please refer to Camunda’s Applicant Privacy Policy. We note that phone numbers and SMS opt-in data are not shared, sold, or rented to third parties or affiliates for marketing or promotional purposes.

By opting in to SMS use, please be aware that:

- Text message frequency may vary;

- Standard text message and data rates may apply; and

- By opting in, you will be enrolled directly in ongoing recruiting text messages from Camunda.

To opt-out, simply select ''No'' when prompted during your application. You can also change your preferences at any time by contacting talent-operations@camunda.com.

---

Benefits found in job post

Assurance invalidité
','https://www.linkedin.com/jobs/view/4476025881/','Senior Software Engineer, Backend - Data Streaming',(SELECT id FROM companies WHERE name='Camunda'),'Budapest Metropolitan Area','unknown','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Exxeta');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4491694908','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4491694908'),'Exxeta

Java Backend Engineer (all genders)

Tbilisi, Georgia · 3 days ago · 38 people clicked apply

Responses managed off LinkedIn

Hybrid

Full-time

## About the job

Exxeta is now in Georgia and we want you to become part of our growing Heartbeat Georgian Team and to make the world more digital and better together. Join us in creating solutions that are unique. We''re excited to get to know you!

As a Java Backend Engineer (all genders) you will work in a motivated team on new digital products and service platforms. You will work independently on the design, development and integration of Backend components and will be significantly involved in the development of ideas, prototyping, conception and implementation.

What awaits you

- Responsibility for the analysis, consulting and implementation of customer requirements regarding architecture, best practices, methods, standards and concepts for the realisation of software solutions within the system landscapes in the area of Java, Spring Boot or Quarkus

- Support of complex IT transformations and projects

- Deepening acquired knowledge and actively contributing to our team, e.g. by exploring new topics and technologies

- Great creative freedom and realisation of your own ideas

- Independent and autonomous conception and implementation of complex software solutions on the basis of current technology stacks and agile procedures

- Technical and professional support in the development of new product and solution ideas

- Ensuring the quality of our software solutions, e.g. through code reviews

- Developing new topics and technologies, deepening acquired knowledge and actively contributing to our team

- Responsibility for the continuous improvement of the development process in the team

What we expect from you

- Responsibility for the analysis, consulting and implementation of customer requirements regarding architecture, best practices, methods, standards and concepts for the realisation of software solutions within the system landscapes in the area of Java, Spring Boot or Quarkus

- Support of complex IT transformations and projects

- Deepening acquired knowledge and actively contributing to our team, e.g. by exploring new topics and technologies

- Great creative freedom and realisation of your own ideas

- Independent and autonomous conception and implementation of complex software solutions on the basis of current technology stacks and agile procedures

- Technical and professional support in the development of new product and solution ideas

- Ensuring the quality of our software solutions, e.g. through code reviews

- Developing new topics and technologies, deepening acquired knowledge and actively contributing to our team

- Responsibility for the continuous improvement of the development process in the team

Why Exxeta

At Exxeta we challenge the traditional concept of consulting and tech. Over 1,200 colleagues at 18 various locations across Europe work together every day to create digital solutions, change markets and transform mindsets - driven by our passion for technology, our team spirit and the drive to create real impact. In other words: High-tech with a heartbeat.

We are a home for people who want to make a difference. Diversity and different perspectives enrich our team. Attitude, ideas, and a drive to get things done - that’s what matters to us!

Apply now!
','https://www.linkedin.com/jobs/view/4491694908/','Java Backend Engineer (all genders)',(SELECT id FROM companies WHERE name='Exxeta'),'Tbilisi (Hybrid)','hybrid','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Spotware Systems');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4494161348','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4494161348'),'Spotware Systems

Senior AI-First Backend Engineer

Limassol, Limassol, Cyprus · 2 days ago · 48 people clicked apply

Responses managed off LinkedIn

On-site

Full-time

## About the job

Spotware Systems is looking for a Senior Backend Engineer to join a new product team in Limassol, Cyprus. The team is building a new AI product from scratch. In this role, you will design backend services and deliver them with AI coding agents (Claude Code, Codex, Cursor or similar) as your main tool. You will own features end-to-end: from an unclear requirement to a tested service in production. This is a role for an experienced engineer who already works this way and wants to help define how a small AI-first team builds software.

Responsibilities

- Design backend services: API contracts, data models, failure handling and scalability

- Turn product requirements into specs and plans that AI agents can execute

- Run several agent sessions in parallel and verify their output with tests, code review and metrics

- Build and improve the team''s agent tooling: AGENTS.md / CLAUDE.md, skills, hooks, MCP servers, CI quality gates

- Review code written by agents and by colleagues, and take full ownership of everything you ship

- Work closely with the Product Manager, Business Analyst, QA and DevOps engineers

Requirements

- 5+ years of professional backend development in production. Main language is not important: Java/Kotlin, Go, C#, Rust, C++ or TypeScript

- Ability to read and review Kotlin/Java and TypeScript code

- Strong service design skills: you can design a new service without step-by-step guidance

- Daily, hands-on use of AI coding agents in real projects, not only autocomplete

- A project you can show where agents wrote most of the code under your direction

- Good understanding of testing and of how to verify code you did not write yourself

- Docker and CI/CD

Nice to have

- Kubernetes

- Experience building a new product from scratch in a small team

- Fintech, trading or broker APIs

- Experience building services with LLMs inside (agents, tool use, evals)

Conditions

- Private medical insurance

- Relocation support (if applicable), including an entry permit, relocation bonus, flight tickets, and airport pickup

- Support with settling into life in Cyprus, including accommodation, banking, healthcare, and everyday guidance

- Comfortable office environment with fresh fruit every day, treats or pizza on Wednesdays, and snack day on Fridays

- Competitive compensation based on your experience, skills, and level of responsibility

- Welcome Baby Bonus and Baby Box

- Birthday voucher

- Partial compensation for sports activities and wellness initiatives

- Greek language classes

Powered by JazzHR

OltrEOJEA6

---

Benefits found in job post

Medical insurance
','https://www.linkedin.com/jobs/view/4494161348/','Senior AI-First Backend Engineer',(SELECT id FROM companies WHERE name='Spotware Systems'),'Limassol, Limassol, Cyprus','unknown','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Patrianna');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4494772865','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4494772865'),'Patrianna

Senior Java Engineer

Ukraine · 2 hours ago · 1 person clicked apply

Responses managed off LinkedIn

Remote

Full-time

## About the job

About Patrianna

Patrianna is a fast-scaling product development company headquartered in Gibraltar, with a dynamic, global team powering our growth. We operate at the intersection of technology and entertainment, building innovative solutions that shape the future of social gaming and deliver outstanding experiences to millions of players worldwide.

We''re driven by speed, ambition, and bold ideas. At our core, we''re product creators and problem-solvers who thrive in a high-performance environment. We''re looking for exceptional talent-smart, adaptable, and motivated individuals eager to make an impact, scale business functions at pace, and continuously improve.

AI-Driven Engineering Culture

We are actively building an AI-driven engineering organization with a clear and deliberate stance: artificial intelligence is a core part of how we build and deliver software — not an experiment, not a trend, and not an optional add-on.

AI is embedded into our engineering workflows, from system design and prototyping to refactoring, testing, and code review. We rely on AI to significantly influence how work is done and to accelerate delivery, while maintaining a high bar for code quality, system reliability, and architectural clarity.

We expect engineers to be fully comfortable working in AI-driven environments and to treat AI as a primary productivity multiplier. Preference for AI-free workflows or resistance to AI-assisted development is NOT aligned with how we operate.

Strong engineering judgment, architectural ownership, and accountability remain essential. AI amplifies our engineers — it does not replace responsibility for technical decisions.

What You Will Be Doing

- Design and deliver clean, scalable, and testable Java-based backend systems with minimal supervision

- Collaborate with cross-functional teams including backend, web, mobile, QA, and product teams

- Take ownership of specific services or functional areas within our large-scale architecture

- Leverage AI tools and assistants as integral parts of your development workflow—from system design to code review

- Contribute to maintaining high test coverage, predictable production behavior, and seamless scalability

- Drive engineering excellence through modern practices and continuous improvement

What We Are Looking For

- Extensive production experience with Java and its ecosystem

- Solid understanding of relational database principles (preferably PostgreSQL) and messaging concepts (preferably Kafka)

- Strong system design, troubleshooting, and debugging capabilities

- Comfortable and proactive in using AI coding assistants (GitHub Copilot, Claude Code, etc.) as productivity multipliers

- Practical experience validating, refining, and taking ownership of AI-generated output

- Upper-intermediate or higher level of English

Why you will love it

You''ll be at the forefront of AI-driven engineering in a fast-scaling environment where your code directly impacts millions of users. Work with cutting-edge technology (latest Java, Spring Boot, GCP, Kubernetes) while enjoying full remote flexibility, generous leave, and real opportunities for growth. Join a team that treats AI as a core engineering tool—not an experiment—and where engineering excellence, speed, and meaningful impact are part of everyday work.
','https://www.linkedin.com/jobs/view/4494772865/','Senior Java Engineer',(SELECT id FROM companies WHERE name='Patrianna'),'Ukraine (Remote)','remote','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Gecko Dynamics');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4292930392','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4292930392'),'Gecko Dynamics

Senior Java Developer

Cracow, Małopolskie, Poland · 1 year ago · Over 100 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

## About the job

We are- https://geckodynamics.com/

Client: SaaS sourcing platform for raw materials acquisition

Stack: Java (19-migrating to 21), Spring, Spring Boot, Hibernate, REST, Docker, Kubernetes, AWS, DB: PostgreSQL and MongoDB

Responsible For

- new features, product development

- MVP

- Microservices

Technical Expectations

- Powerful Java experience, familiar with recent releases, common data structures, and JDK APIs

- Extensive Spring Boot experience, with multiple professional experiences in Spring Boot microservices, JPA

- Strong experience with REST and other API protocols

- Solid relational database experience, both in database structure and query language

- Good understanding of git, git flow, GitHub flow, application development lifecycle

Additional Qualifications

- Experience with SaaS platforms and understanding of sourcing platforms.

- Familiarity with DevOps tools such as Docker, Kubernetes, and GitHub, as well as any Cloud experience, is a plus.

- Strong communication skills and ability to work in a collaborative, cross-functional team.

About Gecko Dynamics

Gecko Dynamics is a web & custom software development company, specializing in the development of SaaS applications, Business Process Management systems, refactoring & recovery of poor-code projects, and development of mobile applications. We have delivered high-performance and stable software solutions since 2002.
','https://www.linkedin.com/jobs/view/4292930392/','Senior Java Developer',(SELECT id FROM companies WHERE name='Gecko Dynamics'),'','unknown','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Miratech');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4429451670','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4429451670'),'Miratech

Senior Java Developer

Warsaw, Mazowieckie, Poland · 4 months ago · 92 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

## About the job

Miratech helps visionaries change the world. We are a global IT services and consulting company that brings together enterprise and start-up innovation. Today, we support digital transformation for some of the world''s largest enterprises. By partnering with both large and small players, we stay at the leading edge of technology, remain nimble even as a global leader, and create technology that helps our clients further enhance their business. We are a values-driven organization and our culture of Relentless Performance has enabled over 99% of Miratech''s engagements to succeed by meeting or exceeding our scope, schedule, and/or budget objectives since our inception in 1989.

Miratech has coverage across 5 continents and operates in over 25 countries around the world. Miratech retains nearly 1000 full-time professionals, and our annual growth rate exceeds 25%.

Job Description

We are looking to hire a Senior Java Developer who will take part in development and maintenance services for customized software solutions for large European customer, a provider of innovative software solutions for the public sector.

The client is a technology service provider specializing in digital solutions for public sector organizations. The company focuses on developing secure, scalable platforms that support administrative processes, data management, and digital transformation initiatives.

The project includes software application development in the tax administration domain. The system under development is the tax administration system that covers taxation processes in one of the European countries. This is the complex system that covers the full life-cycle of tax declarations processing and tax calculations and assessments that included integration with multiple external systems. The project team consist of 20+ people.

Responsibilities:

- Implement software components according to the design specifications

- Perform system integration and testing, problem troubleshooting, and resolution

- Perform unit testing of software components

- Participate in estimating project efforts and delivery timelines

- Proactively collaborate with teammates and communicate with the Project Stakeholders to ensure that the Project efforts proceed efficiently and with predictability

- Provide technical input into product roadmaps

- Participate in triage activities (incident analysis and prioritization)

- Support and optimize CI/CD processes

Project Tech Stack:

Backend (Java): Spring, Quarkus. Frontend: Angular (latest versions)

Data & Integration: PostgreSQL,RESTEasy, Hibernate / Panache, Kafka

Architecture, DevOps & Tools: Microservices, Maven, Flyway, Docker, Kubernetes, GitLab, Jira, ArgoCD, Grafana

Qualifications

- 6+ years of experience in Java development

- Strong experience with Spring Framework, Quarkus

- Understanding of microservices architecture and containers (Docker/Kubernetes)

- Experience with relational databases (PostgreSQL or other RDBMS)

- Experience with REST/SOAP integrations and messaging (Kafka, ActiveMQ)

- Good understanding of testing principles

- Ability to communicate confidently in English

- Strong communication and collaboration skills with stakeholders and distributed teams

Nice to have:

- Experience in Angular front-end development

- German or Swiss German language skills

Additional Information

We offer:

- Culture of Relentless Performance: join an unstoppable technology development team with a 99% project success rate and more than 30% year-over-year revenue growth.

- Competitive Pay and Benefits: enjoy a comprehensive compensation and benefits package, including health insurance, and a relocation program.

- Work From Anywhere Culture: make the most of the flexibility that comes with remote work.

- Growth Mindset: reap the benefits of a range of professional development opportunities, including certification programs, mentorship and talent investment programs, internal mobility and internship opportunities.

- Global Impact: collaborate on impactful projects for top global clients and shape the future of industries.

- Welcoming Multicultural Environment: be a part of a dynamic, global team and thrive in an inclusive and supportive work environment with open communication and regular team-building company social events.

- Social Sustainability Values: join our sustainable business practices focused on five pillars, including IT education, community empowerment, fair operating practices, environmental sustainability, and gender equality.

- Miratech is an equal opportunity employer and does not discriminate against any employee or applicant for employment on the basis of race, color, religion, sex, national origin, age, disability, veteran status, sexual orientation, gender identity, or any other protected status under applicable law.

---

Benefits found in job post

Medical insurance
','https://www.linkedin.com/jobs/view/4429451670/','Senior Java Developer',(SELECT id FROM companies WHERE name='Miratech'),'','unknown','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Alignerr');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4482011832','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4482011832'),'source: linkedin
raw_file: C:/Users/user/Documents/Seeker/Data/raw/linkedin/pages/4482011832.html
source_url: https://www.linkedin.com/jobs/view/4482011832/

Alignerr
Share
Show more options
Senior Software Engineer (AI Infrastructure)
Cracow, Małopolskie, Poland · Reposted 2 days ago · 23 people clicked apply
Promoted by hirer · Responses managed off LinkedIn
$20/hr - $100/hr
Remote
Contract
Save Senior Software Engineer (AI Infrastructure) at Alignerr
Senior Software Engineer (AI Infrastructure)
Alignerr · Cracow, Małopolskie, Poland (Remote)
Save Senior Software Engineer (AI Infrastructure) at Alignerr
Show more options
Assessing your job match
About the job
About The Role
What if your engineering skills could directly shape the platforms and systems behind the AI revolution? We''re looking for Senior Software Engineers in Kraków to design, build, and scale the infrastructure that powers cutting-edge AI products used by millions of people worldwide.
This is high-impact, technically challenging work — the kind that keeps great engineers engaged. You''ll tackle real architectural problems, write code that matters, and collaborate with some of the sharpest minds in AI research and engineering.
This is a fully remote, flexible contract role. If you''re an experienced engineer in Kraków who thrives on autonomy, writes clean and scalable code, and wants to work at the frontier of technology — we want to hear from you.
Organization: Alignerr
Type: Hourly Contract
Location: Remote
Commitment: 20–40 hours/week
What You''ll Do
Design, develop, and maintain production-grade software systems that support AI training, evaluation, and deployment pipelines
Write clean, well-tested, and performant code in Python, TypeScript, or other modern languages
Build and optimize APIs, data pipelines, and backend services that operate reliably at scale
Architect solutions that balance speed of delivery with long-term maintainability
Troubleshoot complex technical issues across distributed systems and cloud infrastructure
Collaborate asynchronously with cross-functional teams — including ML engineers, researchers, and product managers
Participate in code reviews and contribute to engineering best practices across the team
Work independently on well-scoped projects with meaningful ownership and autonomy
Who You Are
5+ years of professional software engineering experience
Strong proficiency in at least one modern programming language — Python, TypeScript/JavaScript, Go, Java, or similar
Solid understanding of software architecture, design patterns, and system design principles
Experience building and deploying backend services, APIs, or data-intensive applications
Comfortable working with cloud platforms (AWS, GCP, or Azure) and containerized environments
Familiar with version control (Git), CI/CD workflows, and modern development tooling
Strong debugging and problem-solving skills — you can navigate ambiguity and find root causes efficiently
Excellent written communication skills — you can document decisions, explain tradeoffs, and collaborate effectively in an async-first environment
Self-directed and reliable — you manage your own time and deliver consistently without close supervision
Nice to Have
Experience with ML/AI infrastructure — training pipelines, model serving, data annotation platforms, or evaluation frameworks
Familiarity with large-scale data processing tools (Spark, Kafka, Airflow, or similar)
Background in distributed systems, microservices architecture, or event-driven design
Experience with infrastructure-as-code (Terraform, Pulumi) or Kubernetes
Contributions to open-source projects or a public portfolio of technical work
Previous experience in a remote-first or async-heavy engineering culture
Why Join Us
Work on cutting-edge AI infrastructure alongside world-class research labs and engineering teams
Fully remote and flexible — set your own schedule and work from anywhere
Tackle genuinely hard technical problems with real-world impact at scale
High autonomy and ownership — no micromanagement, just meaningful engineering work
Collaborate with a global community of top-tier engineers and researchers
Potential for ongoing work, expanded scope, and long-term contract extension as projects grow
Loading job details
','https://www.linkedin.com/jobs/view/4482011832/','Senior Software Engineer (AI Infrastructure)',(SELECT id FROM companies WHERE name='Alignerr'),'Cracow, Małopolskie, Poland (Remote)','remote','$20/hr - $100/hr 2 days ago Be an early applicant',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Binance');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4483298612','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4483298612'),'Binance

# Backend Java Engineer - Kazakhstan/Russian speaker（6 Month Contract）

Kazakhstan · 2 days ago · 45 applicants

Actively reviewing applicants

Remote

Full-time

## About the job

Binance is a leading global blockchain ecosystem behind the world’s largest cryptocurrency exchange by trading volume and registered users. We are trusted by 300+ million people in 100+ countries for our industry-leading security, user fund transparency, trading engine speed, deep liquidity, and an unmatched portfolio of digital-asset products. Binance offerings range from trading and finance to education, research, payments, institutional services, and more. Our mission is to increase the freedom of money and financial access for people around the world.

About The Role

- We are looking for a seasoned Software Backend Engineer to design and improve Kazakhstan Infrastructure/services to the next evolution. To ensure Binance KZ services are reliable, fault-tolerant, efficiently scalable, and cost-effective. The ideal talent should have strong Backend Development experience. Must be able to speak Kazakh or Russian language to liaise and collaborate efficiently with Kazakhstan external stakeholders i.e. Banking parties, Government relations. *100% Remote, Work from Home. This is a 6 months Contractor role to start off with.

 What You’ll Do

- Design, develop, and maintain backend services and APIs for global products using Java.

- Build a high-performance, highly available, and secure backend system to provide robust support for BigPay Fiat business operations.

- Engage in and improve the whole lifecycle of service, from inception and design, through to deployment, operation, and refinement.

- Develop and maintain tools, re-designing capacity planning infrastructure for greater scalability.

- Troubleshooting, diagnosing, fixing software issues, and ensuring data security, and end-to-end system architecture.

 What We’re Looking For

- Minimum of 7 years of experience in software development and architecture.

- Strong expertise in Java and backend frameworks (e.g., Spring Boot, RESTful APIs)

- Experience with databases (SQL and/or NoSQL), messaging systems (e.g., Kafka, RabbitMQ), and microservices architecture

- In-depth understanding of Linux and computer networks.

- Experience in profiling, benchmarking and optimizing ML applications

- Ability to speak in English and Kazakhstan/Russian language is required to coordinate with external Kazakhstan business partners and stakeholders.

 Nice To Haves

- Java code development experience, preferably Apache open source project developers

- Experience in high availability, high performance, and resource optimization.

 Why Binance

- Shape the future of finance

- Collaborate with world-class talent in a user-centric global organization with a flat structure

- Tackle challenging projects in a fast-paced and innovative enviornment with autonomy

- Thrive in a results-driven workplace with opportunities for career growth and continuous learning

- Competitive salary and company benefits

- Remote-first, work-from-home arrangement (may vary depending on the nature of the role and team)

 Binance is committed to being an equal opportunity employer. We believe that having a diverse workforce is fundamental to our success.

By submitting a job application, you acknowledge that you have read our Candidate Privacy Notice.

We may use artificial intelligence (AI) tools to support parts of the hiring process, such as reviewing applications, analyzing resumes, or assessing responses and identifying potential inconsistencies or verification signals in application materials based on available information. These tools assist our recruitment team but do not replace human judgment. Final hiring decisions are ultimately made by humans. If you would like more information about how your data is processed, please contact us.
','https://www.linkedin.com/jobs/view/4483298612/','Backend Java Engineer - Kazakhstan/Russian speaker（6 Month Contract）',(SELECT id FROM companies WHERE name='Binance'),'Kazakhstan (Remote)','remote','',1);
INSERT OR IGNORE INTO companies(name) VALUES ('Alignerr');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4483522343','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4483522343'),'source: linkedin
raw_file: C:/Users/user/Documents/Seeker/Data/raw/linkedin/pages/4483522343.html
source_url: https://www.linkedin.com/jobs/view/4483522343/

Alignerr
Share
Show more options
Software Engineer — AI Code Ranking
Warsaw, Mazowieckie, Poland · 2 days ago · 5 people clicked apply
Responses managed off LinkedIn
$50/hr - $70/hr
Remote
Contract
Save Software Engineer — AI Code Ranking at Alignerr
Software Engineer — AI Code Ranking
Alignerr · Warsaw, Mazowieckie, Poland (Remote)
Save Software Engineer — AI Code Ranking at Alignerr
Show more options
Assessing your job match
About the job
About The Role
What if your years of software engineering experience could directly shape how AI writes code for millions of developers? We''re looking for Software Engineers in Warsaw to evaluate, rank, and refine AI-generated code — helping the next generation of coding assistants produce solutions that are correct, efficient, idiomatic, and production-ready.
Poland''s thriving tech ecosystem has produced world-class engineers, and your expertise is exactly what''s needed. This isn''t about writing boilerplate — it''s about applying expert judgment to push the boundaries of what AI can do with code.
This is a fully remote, flexible contract role built for experienced developers who want meaningful, high-impact work on their own schedule.
Organization: Alignerr
Type: Hourly Contract
Location: Remote
Commitment: 10–40 hours/week
What You''ll Do
Review and rank AI-generated code solutions across multiple programming languages and problem domains
Evaluate code for correctness, efficiency, readability, maintainability, and adherence to best practices
Identify bugs, anti-patterns, security issues, and performance bottlenecks in AI-generated outputs
Write detailed, structured feedback explaining why one solution is better than another
Refine and improve AI-generated code to meet production-level engineering standards
Provide preference rankings that help AI models learn what "good code" actually looks like
Work independently and asynchronously — fully on your own schedule
Who You Are
Software engineer with 3+ years of professional development experience
Proficient in one or more major programming languages (e.g., Python, JavaScript/TypeScript, Java, C++, Go, Rust)
Strong grasp of data structures, algorithms, and computational complexity
Deep understanding of software design principles, clean code practices, and engineering trade-offs
Experienced with code review — you know how to assess quality and articulate why something works or doesn''t
Clear, concise written communicator who can explain technical decisions effectively in English
Self-motivated and reliable when working independently without supervision
Nice to Have
Experience across multiple languages, frameworks, or tech stacks
Background in systems design, distributed systems, or backend architecture
Familiarity with AI/ML concepts or experience using AI coding tools (Copilot, ChatGPT, etc.)
Prior experience with code evaluation, technical mentoring, or open-source contribution
Knowledge of security best practices and performance optimization
Experience in competitive programming or technical interviewing
Why Join Us
Work on cutting-edge AI projects alongside leading research labs
Directly influence how AI coding assistants learn to write better software
Fully remote and flexible — work when and where it suits you
Freelance autonomy with the structure of meaningful, task-based work
Leverage your hard-earned engineering expertise in a new, high-impact way
Potential for ongoing work and contract extension as new projects launch
Join a global community of top-tier engineers shaping the future of AI-assisted development
Loading job details
','https://www.linkedin.com/jobs/view/4483522343/','Software Engineer — AI Code Ranking',(SELECT id FROM companies WHERE name='Alignerr'),'Warsaw, Mazowieckie, Poland (Remote)','remote','$50/hr - $70/hr 2 days ago Be an early applicant',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Solveva');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4484449338','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4484449338'),'Solveva

# Senior Backend Engineer, Agentic Development

Georgia · 1 day ago · 30 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

## About the job

About the project

At Solveva, we develop innovative software solutions that help clients modernize and optimize core insurance processes, including pricing, underwriting, and risk management. By combining domain expertise with modern software engineering practices, we deliver both customized client solutions and proprietary products used across the industry. This position is part of our client delivery team, working on tailored solutions for international clients and their business-critical initiatives.

Your responsibilities

- Developing and implementing complex software applications based on event-driven architectures with Java and Spring.

- Collaborating within cross-functional agile teams throughout the entire SDLC.

- Applying development best practices, including the effective and responsible use of AI coding agents, to ensure code quality, scalability, and maintainability.

- Utilizing AI coding agents while taking ownership of code produced by AI tools through effective guidance, review, validation, and continuous improvement.

- Encouraging technical discussions within the team to drive continuous product improvement.

- Communicating and collaborating effectively with product owners, business analysts, and software developers in an international team.

- Applying your analytical and problem-solving skills to understand the project’s environment and to identify obstacles and solutions.

Your skills and experience

- Bachelor’s degree related to IT or an equivalent practical track record.

- English proficiency (minimum B2 level).

- Solid understanding of the Software Development Life Cycle.

- Hands-on development experience with AI coding agents (e.g. Claude Code), directing and owning the agent-produced code rather than using AI as autopilot.

- Making codebases agent-ready - organizing repos so coding agents can navigate them effectively, writing and maintaining agent-facing docs like CLAUDE.md, and building reusable workflow assets: Skills, prompt templates, etc.

- Task specification for agents - writing feature requests and specs an agent can execute against, with acceptance criteria concrete enough to verify.

- Solid hands-on work experience with Java, Spring application development, ORM (Hibernate, JPA, Spring Data).

- Experience working with message queues (especially Kafka).

- Experience in building distributed systems and microservices, knowledge of typical patterns and service integration issues.

- Experience in designing event-driven architecture.

- Proven experience working with relational and/or NoSQL databases, ability to optimize queries.

- Commitment to product quality, knowledge of testing approaches, code verification and analysis, and design patterns.

- Reliable and self-driven team player.

- Proactive, communicative, strong analytical capabilities.

- Service orientation with a strong focus on customer satisfaction.

- Ability to cope with tight deadlines.

Nice to Have

- Experience in proof-of-concept projects is a plus.

- Experience with warehouse optimization for growing application load, data collection acceleration, and aggregation for analytics is a plus.

- Experience applying AI tools throughout the SDLC and enhancing agent capabilities by integrating them with external systems, tools, and platforms (e.g., via MCP).

About Solveva

Solveva is an owner-operated technology and consulting company specializing in insurance and reinsurance solutions. We operate across Switzerland, Bulgaria, Georgia, Portugal, and the UAE, bringing together teams with strong engineering capabilities and deep industry expertise.

We value trust, openness, and personal responsibility. With a flat structure and collaborative way of working, we encourage initiative, continuous learning, and transparent communication. Our goal is to create an environment where people can grow, contribute meaningfully, and work together to build impactful solutions.

Reasons you’ll love working at Solveva

- Well-defined work setup - each role clearly states whether it is remote or hybrid, so you know exactly what to expect.

- Supportive team environment - work in small, collaborative teams where ideas are shared openly and decisions are made quickly.

- Continuous development - a dedicated learning budget, mentorship, and knowledge-sharing culture to help you grow in your career.

- Modern tools that work for you - choose the hardware that supports your productivity (Windows or Mac).

- A culture of trust and autonomy - we give you space to take ownership, contribute meaningfully, and do your best work.
','https://www.linkedin.com/jobs/view/4484449338/','Senior Backend Engineer, Agentic Development',(SELECT id FROM companies WHERE name='Solveva'),'Georgia (Remote)','remote','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('ITSG Global');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4484474955','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4484474955'),'ITSG Global

Senior Java Developer

Warsaw, Mazowieckie, Poland · 5 days ago · 64 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

## About the job

Kim jesteśmy?

Jesteśmy ITSG Global, firmą software’ową, która od 2007 roku tworzy i rozwija rozwiązania technologiczne dla międzynarodowych klientów. Realizujemy projekty m.in. w obszarze systemów backendowych, platform IoT, rozwiązań chmurowych oraz AI.

Obecnie poszukujemy Senior Java Backend Developera, który dołączy do zespołu rozwijającego i utrzymującego platformę przemysłowego IoT.

To rola dla osoby, która lubi pracować z działającymi produktami, tworzyć nowe funkcjonalności i rozwiązywać problemy techniczne we współpracy z innymi developerami.

O Projekcie

Pracujemy nad platformą przemysłowego IoT CENTERSIGHT, która łączy maszyny i urządzenia terenowe z chmurą oraz umożliwia monitorowanie ich działania.

Rozwiązanie zbiera i przetwarza dane telemetryczne, wspiera analizę stanu urządzeń, obsługę zdarzeń i alarmów oraz zarządzanie zasobami. Platforma i aplikacje oparte na niej są wykorzystywane przez klientów z różnych branż przemysłowych.

Dołączysz do zespołu współpracującego z naszym niemieckim partnerem- wiodącym dostawcą rozwiązań IoT . Na początku będziesz poznawać platformę wspólnie z obecnymi specjalistami, a następnie rozwijać jej komponenty i wspierać projekty klientów. W zespole pracuje już Tech Lead, z którym będziesz współpracować przy podejmowaniu decyzji technicznych.

Główny stack backendowy obejmuje Javę, Spring Boot i Azure. W części rozwiązań wykorzystywany jest również Kotlin, m.in. w testach, a wybrane aplikacje korzystają z TypeScript i Node.js.

Praca obejmuje zarówno rozwój nowych funkcjonalności i integracji, jak i utrzymanie działających systemów produkcyjnych- diagnozowanie problemów, aktualizacje i poprawę niezawodności.

Zakres Zadań w Projekcie

- Projektowanie, implementacja i rozwój usług backendowych w Javie i Spring Boot.

- Rozwój platformy IoT oraz integracji z systemami klientów i usługami chmurowymi.

- Tworzenie i rozwijanie API oraz praca z bazami danych.

- Analiza i rozwiązywanie błędów w istniejących aplikacjach produkcyjnych.

- Tworzenie testów automatycznych i udział w code review.

- Współpraca z Tech Leadem, developerami oraz specjalistami DevOps przy projektowaniu i wdrażaniu zmian.

- Monitorowanie działania aplikacji, analiza logów oraz wsparcie w rozwiązywaniu incydentów.

- Aktualizacja zależności, rozwój dokumentacji i udział w usprawnianiu procesów technicznych.

- Współpraca z zespołem partnera przy poznawaniu platformy i jej dalszym rozwoju.

- Korzystanie z narzędzi AI wspierających programowanie oraz weryfikacja wygenerowanego kodu.

Oczekiwane Kompetencje

- Minimum 5 letnie komercyjne doświadczenie w tworzeniu usług backendowych.

- Bardzo dobra znajomość Javy i Spring Boot.

- Praktyczne doświadczenie z Microsoft Azure w środowisku produkcyjnym.

- Doświadczenie w rozwijaniu i utrzymywaniu aplikacji produkcyjnych.

- Znajomość projektowania REST API i integracji systemów.

- Praktyczna znajomość SQL i relacyjnych baz danych, najlepiej PostgreSQL.

- Doświadczenie w tworzeniu testów jednostkowych i integracyjnych, np. z wykorzystaniem JUnit i Mockito.

- Znajomość code review oraz procesów CI/CD.

- Umiejętność diagnozowania problemów na podstawie logów i działania aplikacji.

- Otwartość na pracę z istniejącym kodem, w tym jego aktualizację i modernizację.

- Znajomość języka angielskiego pozwalająca na swobodną komunikację z zespołem.

- Dostępność do rozpoczęcia współpracy w możliwie krótkim czasie.

Mile Widziane

- Doświadczenie w projektach IoT lub systemach przetwarzających dane telemetryczne.

- Znajomość TypeScript i Node.js.

- Znajomość Kotlina lub chęć poznania tego języka.

- Doświadczenie z Kafka, Azure Event Hubs lub Azure Service Bus.

- Znajomość Azure Data Explorer, Azure IoT Hub lub innych usług Azure związanych z IoT.

- Doświadczenie z Dockerem, Kubernetes i GitLab CI.

- Znajomość JPA/Hibernate oraz narzędzi do migracji baz danych, np. Flyway lub Liquibase.

- Znajomość narzędzi monitorujących, np. Application Insights lub Grafana.

- Doświadczenie z Testcontainers, WireMock lub Cucumber.

- Znajomość języka niemieckiego.

Warunki Współpracy Projektowej

- stawka: 150-180 zł netto+vat/h

- Możliwość pracy 100% zdalnie, hybrydowo lub z biura w Warszawie.

- Udział w rozwoju produkcyjnej platformy przemysłowego IoT i projektów dla międzynarodowych klientów.

- Współpraca z doświadczonym Tech Leadem oraz specjalistami backendu, chmury i DevOps.

- Możliwość poznania rozwiązań IoT, Azure oraz różnych projektów klientów.

- Dodatkowo: możliwość korzystania z lekcji języka angielskiego, karty MultiSport, prywatnej opieki medycznej, wydarzeń integracyjnych i budżetu szkoleniowego.

Proces Rekrutacji

- Rozmowa z rekruterem na temat Twojego doświadczenia, oczekiwań i dostępności.

- Rozmowa techniczna online.

Kontakt: kkuzmicz@itsg.com.pl

Oczekujemy

Oferujemy
','https://www.linkedin.com/jobs/view/4484474955/','Senior Java Developer',(SELECT id FROM companies WHERE name='ITSG Global'),'','unknown','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Patrianna');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4484537811','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4484537811'),'Patrianna

Senior Backend Engineer

Ukraine · 4 days ago · 40 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

## About the job

About Patrianna

Patrianna is a fast-scaling product development company headquartered in Gibraltar, with a dynamic, global team powering our growth. We operate at the intersection of technology and entertainment, building innovative solutions that shape the future of social gaming and deliver outstanding experiences to millions of players worldwide.

We''re driven by speed, ambition, and bold ideas. At our core, we''re product creators and problem-solvers who thrive in a high-performance environment. We''re looking for exceptional talent-smart, adaptable, and motivated individuals eager to make an impact, scale business functions at pace, and continuously improve.

AI-Driven Engineering Culture

We are actively building an AI-driven engineering organization with a clear and deliberate stance: artificial intelligence is a core part of how we build and deliver software — not an experiment, not a trend, and not an optional add-on.

AI is embedded into our engineering workflows, from system design and prototyping to refactoring, testing, and code review. We rely on AI to significantly influence how work is done and to accelerate delivery, while maintaining a high bar for code quality, system reliability, and architectural clarity.

We expect engineers to be fully comfortable working in AI-driven environments and to treat AI as a primary productivity multiplier. Preference for AI-free workflows or resistance to AI-assisted development is NOT aligned with how we operate.

Strong engineering judgment, architectural ownership, and accountability remain essential. AI amplifies our engineers — it does not replace responsibility for technical decisions.

What You Will Be Doing

- Design and deliver clean, scalable, and testable Java-based backend systems with minimal supervision

- Collaborate with cross-functional teams including backend, web, mobile, QA, and product teams

- Take ownership of specific services or functional areas within our large-scale architecture

- Leverage AI tools and assistants as integral parts of your development workflow—from system design to code review

- Contribute to maintaining high test coverage, predictable production behavior, and seamless scalability

- Drive engineering excellence through modern practices and continuous improvement

What We Are Looking For

- Extensive production experience with Java and its ecosystem

- Solid understanding of relational database principles (preferably PostgreSQL) and messaging concepts (preferably Kafka)

- Strong system design, troubleshooting, and debugging capabilities

- Comfortable and proactive in using AI coding assistants (GitHub Copilot, Claude Code, etc.) as productivity multipliers

- Practical experience validating, refining, and taking ownership of AI-generated output

- Upper-intermediate or higher level of English

Why you will love it

You''ll be at the forefront of AI-driven engineering in a fast-scaling environment where your code directly impacts millions of users. Work with cutting-edge technology (latest Java, Spring Boot, GCP, Kubernetes) while enjoying full remote flexibility, generous leave, and real opportunities for growth. Join a team that treats AI as a core engineering tool—not an experiment—and where engineering excellence, speed, and meaningful impact are part of everyday work.
','https://www.linkedin.com/jobs/view/4484537811/','Senior Backend Engineer',(SELECT id FROM companies WHERE name='Patrianna'),'','unknown','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Emerging Travel Group');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4486442166','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4486442166'),'Emerging Travel Group

# Middle Backend Developer (Java/Kotlin)

Georgia · 17 hours ago · 4 applicants

No response insights available yet

Remote

Full-time

## About the job

We are looking for a Middle Backend Developer (Java/Kotlin).

What We Offer: the opportunity to influence technical and architectural decisions; involvement in the full development lifecycle, from design to deployment; hands-on experience with Big Data technologies and distributed systems; technical mentorship and opportunities for professional growth.

About The Project

We develop a Big Data platform and tools focused on:

- Data Ingestion — collecting and delivering data from business systems to an analytical data warehouse.

- Data Maintenance — maintaining and optimizing data storage.

- Analytics Integration — integrating various analytics services and tools into the data platform

 Our Tech Stack

- Backend: Java, Python, Rust, Spring Boot

- Data Streaming & CDC: Apache Kafka, Kafka Connect, Debezium

- Data Processing & Storage: Apache Spark, Apache Iceberg, Trino, Ozone S3

- Database: PostgreSQL

- Infrastructure: Kubernetes

 Requirements

Technical Requirements

Must-have

- Java and/or Kotlin

- Spring Boot

- Apache Kafka

- Relational databases (PostgreSQL)

 Nice-to-have

- Rust and/or Python

- Apache Spark

- S3-compatible object storage

- Experience with distributed systems and Big Data technologies

 What We Expect

- A proactive mindset and willingness to take ownership of tasks

- Interest in contributing to technical discussions, design decisions, and architecture

- Willingness to learn new technologies, receive feedback, and grow with guidance from the Tech Lead

 Benefits

- Flexible schedules and the opportunity to work remotely

- Ambitious and supportive team who love what they do, appreciate each other, and grow together

- Internal programs for adaptation and training, development of soft skills, and leadership abilities

- Partial compensation for participating in external training and conferences

- Corporate English school: Group and individual lessons, speaking clubs with colleagues from all over the world

- Corporate prices on hotels and travel services

- MyTime Day Off - an extra non-working day without loss of compensation

 Learn more about our data protection practices in our Privacy Policy: emergingtravel.com/career/hr-policy
','https://www.linkedin.com/jobs/view/4486442166/','Middle Backend Developer (Java/Kotlin)',(SELECT id FROM companies WHERE name='Emerging Travel Group'),'Georgia (Remote)','remote','',1);
INSERT OR IGNORE INTO companies(name) VALUES ('Patrianna');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4486543763','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4486543763'),'Patrianna

Senior Java Engineer

Ukraine · 1 day ago · 36 people clicked apply

Responses managed off LinkedIn

Remote

Full-time

## About the job

About Patrianna

Patrianna is a fast-scaling product development company headquartered in Gibraltar, with a dynamic, global team powering our growth. We operate at the intersection of technology and entertainment, building innovative solutions that shape the future of social gaming and deliver outstanding experiences to millions of players worldwide.

We’re driven by speed, ambition, and bold ideas. At our core, we’re product creators and problem-solvers who thrive in a high-performance environment. We’re looking for exceptional talent-smart, adaptable, and motivated individuals eager to make an impact, scale business functions at pace, and continuously improve.

AI-Driven Engineering Culture

We are actively building an AI-driven engineering organization with a clear and deliberate stance: artificial intelligence is a core part of how we build and deliver software — not an experiment, not a trend, and not an optional add-on.

AI is embedded into our engineering workflows, from system design and prototyping to refactoring, testing, and code review. We expect engineers to be fully comfortable working in AI-driven environments and to treat AI as a primary productivity multiplier. Strong engineering judgment, architectural ownership, and accountability remain essential—AI amplifies our engineers, it doesn’t replace responsibility for technical decisions.

What You Will Be Doing

- Design and deliver clean, scalable, and testable Java-based backend systems with minimal supervision

- Collaborate with cross-functional teams including backend, web, mobile, QA, and product teams

- Take ownership of specific services or functional areas within our large-scale architecture

- Leverage AI tools and assistants as integral parts of your development workflow—from system design to code review

- Contribute to maintaining high test coverage, predictable production behavior, and seamless scalability

- Drive engineering excellence through modern practices and continuous improvement

Main technologies we use:

- Java 25

- Spring Boot 4

- Spring 7

- Google Cloud Platform

- Kubernetes

- Kafka

- AlloyDB for PostgreSQL

- Spanner

- Ebean ORM

- Flyway

- Redis

- Quartz

- Websockets

- Retool

- Test Containers

- Locust for perf testing

- Jenkins/ArgoCD

- ELK/Sentry/Grafana

- Jira/Confluence/Slack/Bitbucket

What We Are Looking For

- Extensive production experience with Java and its ecosystem

- Solid understanding of relational database principles (preferably PostgreSQL) and messaging concepts (preferably Kafka)

- Strong system design, troubleshooting and debugging capabilities

- Comfortable and proactive in using AI coding assistants (GitHub Copilot, Claude Code, etc.) as productivity multipliers

- Practical experience validating, refining, and taking ownership of AI-generated output

- Upper-intermediate or higher level of English

Why you will love it

You’ll be at the forefront of AI-driven engineering in a fast-scaling environment where your code directly impacts millions of users. Work with cutting-edge technology (latest Java, Spring Boot, GCP, Kubernetes) while enjoying full remote flexibility, generous leave, and real opportunities for growth. Join a team that treats AI as a core engineering tool—not an experiment—and where engineering excellence, speed, and meaningful impact are part of everyday work.
','https://www.linkedin.com/jobs/view/4486543763/','Senior Java Engineer',(SELECT id FROM companies WHERE name='Patrianna'),'','unknown','',0);
INSERT OR IGNORE INTO companies(name) VALUES ('Innovecs');
INSERT INTO source_jobs(source,source_job_id,processing_status,collection_run_id) VALUES ('linkedin','4487731338','RELEVANT','eval-linkedin-27');
INSERT INTO source_job_texts(source_job_ref,readable_text,source_url,title,company_id,collected_location,collected_workplace,collected_salary,easy_apply) VALUES ((SELECT id FROM source_jobs WHERE source='linkedin' AND source_job_id='4487731338'),'Innovecs

Senior Back-End Engineer (Java, AI-Native)

Ukraine · 1 day ago · 57 applicants

No response insights available yet

Remote

Full-time

Easy Apply

## About the job

We are looking for an experienced Senior Back-End Engineer (Java, AI-Native).

Our US-based client is a national warehouse operator with dozens of facilities located across the United states. The project goal is replacing a legacy Warehouse Management System (WMS) with a modern one, supporting various warehouse operations and practices, including 3rd party integrations.

Our development team values diligence, mutual respect, and getting things done.

Innovecs is a global digital services company with a presence in the US, the UK, the EU, Israel, Australia, and Ukraine. Specializing in software solutions, the Innovecs team has experience in Supply Chain, Healthtech, Collaboration Tech, and Gaming.

For the fifth year in a row, Innovecs is included in the Inc. 5000, the list of fastest-growing private companies in the US, and a ranking of the best global outsourcing service providers by IAOP. Recently, Innovecs was honored with the prestigious Global Good Awards for the Employee Engagement & Wellbeing, won gold at the Employer Brand Management Awards, and was included in the Global Top 100 Inspiring Workplaces Ranking.

Our value to you:

- Flexible hours and remote-first mode

- Competitive compensation

- Complete Hardware/Software setup – anything you need for work

- Open-door culture, transparent communication, and top management at a handshake distance

- Health insurance, vacation, sick leaves, holidays, paid maternity/paternity leave

- Access to our learning & development center: workshops, webinars, training platform, and edutainment events

- Virtual team buildings and social activities to celebrate the Innovecs lifestyle

If you feel like you’re the perfect match for this role, drop us your CV!

There are no limitations, no barriers when the right people are on your way — apply for the vacancy and succeed with us!

Innovecs is an equal opportunity employer. All hiring decisions are based on professional qualifications, skills, and experience. We are committed to a transparent, merit-based recruitment process that prevents discrimination and ensures equal opportunities for all candidates. Reasonable accommodations are available upon request throughout the recruitment process to support accessibility and inclusion.

Requirements

- 5+ years of commercial software development experience, primarily with Java

- Strong knowledge of OOP, design patterns, clean code, and software engineering principles

- Strong experience with Java EE, Spring 5 (Core, MVC, Security), and Spring Boot 2

- Strong SQL skills and solid understanding of relational database principles

- Experience designing, maintaining, and troubleshooting REST APIs

- Strong debugging, troubleshooting, and root cause analysis skills, including production environments

- Deep experience with AIDLC (AI-Driven Lifecycle) and agentic programming mindset.

- Ability to effectively use AI for codebase analysis, implementation, debugging, production issue investigation, and automated test generation

- Strong unit testing skills with JUnit, Mockito, and related testing practices

- Experience with Git, Maven, Jenkins, and CI/CD pipelines

- Familiarity with application servers such as IBM WebSphere

- Proficiency with Unix/Linux environments and Bash

- Experience working in Agile/Scrum environments

- Bachelor’s degree in Computer Science, Software Engineering, or equivalent practical experience

Nice to have

- Experience with Warehouse Management Systems (WMS), logistics, fulfillment, or other supply chain domains

- Experience with IBM Db2 and/or Oracle Database

- Experience with Docker and containerized environments

- Experience with C++

- Experience supporting large-scale or legacy enterprise applications

- Experience with MCP-based AI tooling and integrations

Responsibilities

- Develop bug fixes, enhancements, and new features within the WMS product.

- Provide production support by investigating incidents, diagnosing complex issues, performing root cause analysis (RCA), and implementing reliable fixes.

- Use AI-assisted engineering tools, including Claude and MCP integrations, to analyze the codebase, investigate production issues, develop solutions, and accelerate test coverage.

- Validate AI-generated code and technical conclusions, ensuring solutions are correct, secure, maintainable, and appropriate for production.

- Perform code reviews and contribute to maintaining high standards for code quality and engineering practices.

- Collaborate within a Scrum team throughout the delivery lifecycle, from requirements analysis and technical design through implementation, testing, and deployment.

---

Benefits found in job post

Medical insurance, Paid paternity leave

---

Requirements added by the job poster

• 5+ years of work experience with Java
','https://www.linkedin.com/jobs/view/4487731338/','Senior Back-End Engineer (Java, AI-Native)',(SELECT id FROM companies WHERE name='Innovecs'),'','unknown','',1);
