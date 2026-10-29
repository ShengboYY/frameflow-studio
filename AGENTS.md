# Working agreements

## Read before changing

- Read `README.md`, `docs/requirements.md`, `docs/architecture.md`,
  `docs/architecture-decisions.md`, and the relevant task/API documents before editing.
- Inspect existing code and more specific `AGENTS.md` files. Frontend work must also
  follow `frontend/AGENTS.md` and the installed Next.js documentation it references.
- Preserve existing user changes. Make the smallest coherent change; avoid unrelated
  refactoring, reformatting, speculative features, and unused dependencies.
- State material assumptions. Ask only when missing information changes scope, public
  behavior, data handling, compatibility, or an irreversible action. Continue independent
  work while waiting and reuse earlier authorization.
- Current delivery is scaffolding only. The Assessment business specification is missing;
  do not invent business workflows, endpoints, schemas, identity rules, or acceptance criteria.

## Stack and architecture

- Backend: Java 17, Maven, Spring Boot 4.0.x, MyBatis Starter 4.0.x, MySQL 8.4 LTS.
  See `backend/pom.xml` for exact versions; do not independently upgrade managed dependencies.
- Frontend: Next.js 16 App Router, React, strict TypeScript, ESLint, npm lockfile, Node.js 22 LTS.
- Infrastructure: Docker Compose and GitHub Actions. Do not add Redis, message brokers,
  AI SDKs, microservices, CQRS, event sourcing, or a workflow framework without a concrete need.
- Use one Maven project and a modular monolith with lightweight DDD and ports/adapters.
  Under `com.frameflow.studio`, organize by `trigger`, `api`, `cases`, `domain`,
  `infrastructure`, and `types`. Business boundaries are `domain.task`, `domain.template`,
  `domain.asset`, and `domain.identity`; group real code by the same business name within other layers.
- This is the project-specific adaptation of ddd-skill 2.2.4 in ADR-006. Use the legal Java
  package `cases`, never `case`. Do not regenerate this existing project with an archetype.
- Create deeper packages only when real implementation needs them. Do not create empty
  business classes, repositories, domain services, strategies, chains, or ports for hypothetical features.
- Domain code must not depend on Spring, MyBatis, HTTP, or infrastructure. Aggregates own
  business invariants and legal state transitions; avoid public setters for aggregate state.
- Case contracts live in `cases.<feature>` and their implementations in `cases.<feature>.impl`.
  They orchestrate use cases, ownership checks, transaction boundaries, and asynchronous work.
  Trigger uses API DTOs and invokes Case contracts, including for simple queries. Cases do not
  depend on HTTP DTOs or concrete infrastructure. API contains contracts, not orchestration.
- Domain repository/query contracts live in `domain.<feature>.adapter.repository`; business
  capability ports in `domain.<feature>.adapter.port`. Domain services remain plain Java and
  are wired outside Domain. Model subpackages use `aggregate`, `entity`, and `valobj` as needed.
  Use-case commands are not automatically domain entities. Do not copy framework annotations
  or unrestricted aggregate setters from skill examples.
- Infrastructure implementations live in `adapter.repository.<feature>` or `adapter.port.<feature>`;
  MyBatis DAOs in `dao.<feature>` and POs in `dao.po.<feature>`; HTTP clients in
  `gateway.<feature>` with remote DTOs in `gateway.dto.<feature>`; wiring in `config`.
  Do not introduce `persistent` or parallel persistence packages. Repositories do not call
  HTTP generation services; generation adapters do not update business tables.
- Application-only execution ports such as TaskDispatcher belong in `cases.<feature>.port`;
  infrastructure may implement these contracts. Local I/O and executor adapters need no fake
  HTTP gateway. These explicit skill adaptations preserve the original project constraints.
- Controllers must not call repositories or DAOs. Do not access another domain's internal
  repositories, aggregates, DAOs, or POs; coordinate through explicit domain capabilities in Cases.
- Avoid cyclic dependencies. `types` contains only genuinely common framework-free types,
  never shared business entities or Spring configuration. Simple queries need no forced aggregates.
- Design aggregate boundaries around actual consistency rules. On first asynchronous write,
  consider optimistic locking or conditional updates. Dispatch after commit, keep external calls
  outside long database transactions, and document the accepted dispatch/crash window.
- Follow the naming rules documented in architecture.md. Select patterns based on actual
  variation and complexity, not a fixed number of checks, branches, or lines.
- Do not silently change core architecture or a public API. Explain the reason, assess
  compatibility, and update the relevant ADR/API document alongside an authorized change.

## Quality and verification

- Keep credentials in environment variables; only sanitized examples belong in Git.
  Validate realistic input failures at external boundaries. Do not log secrets or asset content.
- Explain non-obvious constraints in comments; omit comments that repeat the code.
- Reproduce bugs with a focused test or clear manual check. Add meaningful behavior tests
  when appropriate; avoid tests that merely mirror low-impact implementation details.
- Run relevant checks: backend `mvn --batch-mode --no-transfer-progress verify`;
  frontend `npm ci`, `npm run lint`, `npm run typecheck`, `npm run build`;
  root `docker compose --env-file .env.example config --quiet`.
- For substantial tasks, state a brief plan and observable completion condition. Continue
  authorized work through implementation and verification; do not stop at a plan.
- Review the diff for unrelated changes and accidental edits to user work. Report commands,
  results, environment blockers, and what remains unverified. Context loading does not prove
  database connectivity; Compose parsing does not prove containers run.
- Do not publish, deploy, send messages, or create commits containing unrelated files without
  user authorization. Document completed work and remaining tasks.
