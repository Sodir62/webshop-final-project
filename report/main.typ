#import "template/lib.typ": report, dnode, adown

// Placeholder the team must fill in before submitting (guillemets are literal, no escaping).
#let todo(it) = text(fill: rgb("#b00020"), weight: "bold")[«TODO: #it»]

// An Azure account panel for the deployment diagram.
#let acct(title, region, inner) = rect(
  width: 100%, radius: 5pt, inset: 8pt, stroke: 1pt + luma(120), fill: luma(250),
)[
  #text(weight: "bold", size: 9pt)[#title] \
  #text(size: 8pt, style: "italic", fill: luma(45%))[#region]
  #v(5pt)
  #inner
]

#show: report.with(
  title: "A Broker Webshop for Concert & Festival Packages",
  course: "Distributed Systems — Final Project",
  institution: "KU Leuven",
  campus: "Group T Leuven Campus",
  coaches: ("Bert Lagaisse", "Vivian Tsang", "Wout Tessens"),
  team: todo("team number"),
  authors: (
    [Sodir YUKSEL — #todo("r-number")],
    [Dimitrios KYRANOS — #todo("r-number")],
    [Demirhan YASAR — #todo("r-number")],
    [Celal Emre ERKAN — (r0916432)],
  ),
  period: "Academic year 2025–2026",
)

= Introduction <sec-intro>

Our web shop sells *concert and festival packages*: a ticket to an event plus — optionally —
food and drinks for the evening. The tickets come from one company; the food and drinks from
another. The shop itself is a *broker*: it owns no stock, but composes the products of two
independent supplier companies into one package and sells it as a single, atomic order. The
customer either gets the whole evening or is not charged at all.

That one product contains all the distribution in the system. Each supplier runs its own
service on its own database, in its own data center and administrative domain, and the broker
coordinates them over REST. Counting the broker, that makes three companies — three Azure
accounts, one per team member. Placing an order is therefore a genuine distributed transaction
across organizations, not a database commit inside one application.

@tab-reqs maps the assignment's requirements to the sections that describe how each is met.
The basic level is implemented in full; the Level-2 features (external identity provider,
broker-to-supplier authentication, loosely-coupled asynchronous ordering) are implemented as
*opt-in Spring profiles*, so the basic-level system stays demonstrable on its own.

#figure(
  caption: [Requirement coverage. "Profile" marks Level-2 features activated by configuration.],
  table(
    columns: (1fr, auto, auto),
    align: (left + horizon, left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Requirement*], [*Status*], [*Where*]),
    [Broker = GUI front-end + back-end logic + data storage], [done], [@sec-arch],
    [At least two external supplier services (list / reserve / order)], [done], [@sec-arch],
    [Broker survives unresponsive or malformed suppliers], [done], [@sec-fault],
    [Order = ACID distributed transaction, broker as coordinator], [done], [@sec-tx, @sec-acid],
    [Order succeeds only if items available + address + (simulated) payment], [done], [@sec-tx],
    [Failure scenarios demonstrated (supplier crash, network, broker crash)], [done], [@sec-fault, @sec-testing],
    [Customers order without an account; payment simulated], [done], [@sec-security],
    [Managers authenticate to see all orders], [done], [@sec-security],
    [Azure data store for the broker; suppliers own their DBs; three accounts/regions], [done], [@sec-deploy],
    [Level 2 — external IdP (Auth0) + broker→supplier authentication], [profile `auth0`], [@sec-security],
    [Level 2 — queued ordering with a 15-minute completion window], [profile `async`], [@sec-tx],
    [SQL *and* NoSQL persistence behind one supplier contract], [profile `mongo`], [@sec-nosql],
  ),
) <tab-reqs>

= System architecture <sec-arch>

The system is three independent Spring Boot applications (Java 17, Spring Boot 4) that
communicate over REST.

== The three services

The *broker* (`:8080`) is the only application a person ever sees. It serves the GUI (Spring
MVC + Thymeleaf), runs the transaction coordinator (`AtomicOrderService`), authenticates
managers (Spring Security), and persists orders through Spring Data JPA. It talks to the
suppliers with Spring's `RestClient`, configured with 5 s connect / 10 s read timeouts.

*food-and-beverages* (`:8081`) and *ticket-supplier* (`:8082`) are the two supplier companies.
Each owns its slice of the catalog and the stock behind it. The ticket supplier can run either
on a relational database or, under a separate profile, on MongoDB (@sec-nosql).

The broker distinguishes *three* product types (`TICKET`, `FOOD`, `DRINK`) but routes them to
*two* physical suppliers: ticket calls go to `:8082`, while food and drink calls both go to
`:8081`, which tags each product with a category and serves one kind at a time via `?type=`.
Inside the broker, a `SupplierRegistry` maps each `SupplierType` to a `SupplierClient`; the
real `HttpSupplierClient` is the default, and the `stub` profile swaps in an in-process fake
that the test suites drive.

#figure(
  caption: [Runtime architecture: one broker coordinating two independent supplier services,
    each owning its own database in its own administrative domain.],
  block(width: 100%)[
    #set align(center)
    #dnode("Web browser", body: [Customer (anonymous) and manager · HTML over HTTP],
      fill: luma(245), edge: luma(120), width: 70%)
    #adown(label: [HTTPS · Thymeleaf MVC · manager login])
    #dnode("Broker — Spring Boot :8080", width: 92%, body: [
      Thymeleaf GUI + Spring MVC · Spring Security \
      `AtomicOrderService` — distributed-transaction coordinator · order queue (`async`) \
      Spring Data JPA → *brokerdb* (MySQL local / PostgreSQL on Azure)
    ])
    #grid(columns: (1fr, 1fr), adown(label: [REST (+ M2M JWT under `auth0`)]), adown(label: [REST (+ M2M JWT under `auth0`)]))
    #grid(columns: (1fr, 1fr), gutter: 14pt,
      dnode("food-and-beverages — :8081", body: [
        FOOD + DRINK catalog & stock \
        JPA → *foodbevdb* (MySQL / PostgreSQL)
      ]),
      dnode("ticket-supplier — :8082", body: [
        TICKET catalog & stock \
        JPA → *ticketdb*, or MongoDB (`mongo` profile)
      ]),
    )
  ],
) <fig-arch>

== The supplier interface

Machine-to-machine, the whole system rests on one small B2B contract: the REST interface every
supplier exposes and the broker consumes. Its four operations — list, reserve, confirm, cancel
— are deliberately exactly what a coordinator needs to drive a reserve/confirm transaction
(@sec-tx), and nothing more.

#figure(
  caption: [The supplier B2B REST interface (the broker is the client). Error codes come from
    the suppliers' `ApiExceptionHandler`.],
  table(
    columns: (auto, auto, 1fr, auto),
    align: (left + horizon, left + horizon, left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Operation*], [*HTTP*], [*Purpose*], [*Errors*]),
    [List], [`GET /products` \ (`?type=` on f&b)], [Live catalog with current stock], [—],
    [Reserve], [`POST /reservations` \ `{productId, quantity}` \ → `201 {reservationId}`],
      [Place a cancellable hold; stock is decremented now; the hold carries a TTL
       (@sec-tx)], [`400` invalid \ `404` unknown \ `409` out of stock],
    [Confirm], [`POST /reservations/{id}/confirm` → `200`],
      [Turn a hold into a permanent sale (idempotent)], [`404` unknown \ `409` if cancelled],
    [Cancel], [`DELETE /reservations/{id}` → `204`], [Release a hold (idempotent)], [—],
  ),
) <fig-api>

Everything else is human-facing rather than B2B: the broker's own endpoints (@fig-broker-api)
serve HTML to a browser, not JSON to another service.

#figure(
  caption: [The broker's own HTTP endpoints (browser-facing, HTML). All are anonymous except
    the manager dashboard; the `auth0` profile additionally exposes OIDC login and logout.],
  table(
    columns: (auto, auto, 1fr),
    align: (left + horizon, left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Endpoint*], [*Access*], [*What it does*]),
    [`GET /`], [anonymous], [Landing page: the catalog grid (concerts, plus food and drink
      sections) built from the suppliers' live `list` calls; a supplier that is down just
      renders an empty section instead of breaking the page.],
    [`GET /concerts/{id}`], [anonymous], [Order form for one concert: ticket quantity, optional
      food and drink dropdowns, the delivery address and the (simulated) card fields.],
    [`POST /orders`], [anonymous], [Place an order: validates the form server-side, builds the
      `CustomerOrder`, then runs the distributed transaction synchronously — or, under `async`,
      enqueues the order and redirects immediately. Redirects to the order's page.],
    [`GET /orders/{id}`], [anonymous \ (capability URL)], [Status page for that order; under
      `async` it refreshes itself while the order completes in the background. The unguessable
      UUID is the only access control.],
    [`GET /manager/orders`], [`MANAGER`], [Manager dashboard: every customer's order with its
      current state, newest first.],
    [`GET /oauth2/authorization/auth0` \ `/logout`], [`auth0` \ profile], [OIDC login and
      logout, added by Spring Security only under the `auth0` profile (login round-trips through
      Auth0; logout clears the Auth0 session too).],
  ),
) <fig-broker-api>

== Profiles

One codebase serves every environment; Spring profiles select the behaviour. The defaults are
deliberately the *basic level*, so a checkout of the repository runs and demos with nothing
but Java and a local database.

#figure(
  caption: [Spring profiles. Profiles compose: the deployed system runs `azure,async,auth0`.],
  table(
    columns: (auto, 1fr),
    align: (left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Profile*], [*What it switches*]),
    [_default_], [Basic level: anonymous shop, HTTP-Basic manager login, synchronous ordering,
      local MySQL. No external dependency beyond the database.],
    [`stub`], [Tests only: replaces the HTTP suppliers with in-process fakes with fault-injection
      hooks (down, fail-confirm).],
    [`azure`], [Datasource becomes Azure Database for PostgreSQL; every endpoint and secret comes
      from environment variables — nothing is committed.],
    [`auth0`], [Level 2 access control: customers and managers log in via Auth0 (OIDC); the broker
      presents a machine-to-machine JWT to the suppliers, which validate issuer and audience
      (@sec-security).],
    [`async`], [Level 2 loose coupling: orders are queued on an embedded ActiveMQ Artemis broker
      and completed in the background within a 15-minute window (@sec-tx).],
    [`mongo`], [Ticket supplier only: swaps the JPA/MySQL stack for MongoDB behind the same REST
      contract (@sec-nosql).],
  ),
) <tab-profiles>

= Deployment on Azure <sec-deploy>

Each of the three applications is deployed by a different team member, in a *separate Azure
account* and a *different Azure region*. The three companies in the story are thus three
administrative domains in reality, each with its own compute and its own database, and every
broker–supplier call crosses an account and region boundary. The applications are identical to
the local build; only the active profiles and a handful of environment variables change.

#figure(
  caption: [Deployment over three Azure accounts and regions. The broker calls both suppliers
    over REST across account and region boundaries.],
  grid(columns: (1fr, 1fr, 1fr), gutter: 10pt,
    acct([Azure account A — #todo("owner")], [#todo("region, e.g. West Europe")],
      dnode("Broker :8080", body: [Spring Boot \ PostgreSQL (brokerdb)], width: 100%)),
    acct([Azure account B — (Celal Emre Erkan)], [(Poland Central)],
      dnode("food-and-beverages :8081", body: [Spring Boot \ PostgreSQL (foodbevdb)], width: 100%)),
    acct([Azure account C — #todo("owner")], [#todo("region, e.g. France Central")],
      dnode("ticket-supplier :8082", body: [Spring Boot \ PostgreSQL or MongoDB], width: 100%)),
  ),
) <fig-deploy>

*Configuration.* The `azure` profile reads the datasource and peers from environment variables
(`DB_HOST`/`DB_USER`/`DB_PASSWORD`, `SUPPLIER_*_URL`); `auth0` adds the tenant variables
(`AUTH0_DOMAIN`, client ids/secrets, audience). A missing variable fails at startup rather than half-working. Schemas are created by Hibernate (`ddl-auto=update`).

*Deployed instance.* The VMs run with `--spring.profiles.active=azure,async,auth0`. For the
grading team: the shop is reachable at "http://20.251.203.131:8080/", manager login credentials are 

email: 'webshopmanager\@gmail.com'

 password: 'Demirhan12345.'

 The application stays deployed until grades are received.

= Data model <sec-data>

Nothing is shared at the database level: every service owns its data outright. The broker
stores *what was ordered and how far the transaction got*; each supplier stores *its products
and the holds on them*. Exactly one value crosses the boundary — the opaque `reservationId` a
supplier returns on reserve, which the broker keeps per order item and hands back on confirm
or cancel.

#figure(
  caption: [Entities per service. The broker order is also the durable transaction log.],
  table(
    columns: (auto, 1fr),
    align: (left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Service / entity*], [*Fields (and role)*]),
    [Broker — `CustomerOrder`], [`id` (UUID), `deliveryAddress`, `cardholderName`, `cardLast4`
      (simulated payment), `status` (`OrderStatus`), `createdAt`; `1—*` `OrderItem`],
    [Broker — `OrderItem`], [`supplierType`, `productId`, `productName`, `unitPrice`,
      `quantity`, `reservationId` (the supplier's hold), `status` (`ItemStatus`)],
    [Supplier — `Product`], [`id`, `name`, `description`, `price`, `stock`, (`category` on f&b)],
    [Supplier — `Reservation`], [`id` (UUID), `productId`, `quantity`, `status`
      (`PENDING`/`CONFIRMED`/`CANCELLED`), `createdAt`, `expiresAt` (the hold's TTL)],
  ),
) <fig-data>

@fig-schema gives the physical side of the same model: the tables and columns Hibernate
generates (`ddl-auto=update`) in each of the three independent databases.

#figure(
  caption: [Physical schema of each service's own database — three separate databases, no
    shared tables. The ticket supplier uses these two relational tables by default, or two
    equivalent MongoDB collections (same fields, no column types) under the `mongo` profile.],
  table(
    columns: (auto, auto, 1fr),
    align: (left + horizon, left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Database*], [*Table*], [*Columns* (PK / FK / enum noted; · separates columns)]),

    table.cell(rowspan: 2)[*brokerdb* \ broker],
    [`customer_order`],
    [`id` varchar(36) *PK* (UUID) · `delivery_address` · `cardholder_name` ·
     `card_last4` varchar(4) · `status` enum `OrderStatus` · `created_at` timestamp],
    [`order_item`],
    [`id` bigint *PK* (auto) · `order_id` bigint *FK* → `customer_order` ·
     `supplier_type` enum (TICKET / FOOD / DRINK) · `product_id` · `product_name` ·
     `unit_price` decimal · `quantity` int · `reservation_id` varchar(64) ·
     `status` enum `ItemStatus`],

    table.cell(rowspan: 2)[*foodbevdb* \ food-and-beverages],
    [`product`],
    [`id` varchar(16) *PK* · `name` · `description` · `price` decimal · `stock` int ·
     `category` enum (FOOD / DRINK)],
    [`reservation`],
    [`id` varchar(36) *PK* (UUID) · `product_id` varchar(16) · `quantity` int ·
     `status` enum (PENDING / CONFIRMED / CANCELLED) · `created_at` timestamp ·
     `expires_at` timestamp (TTL)],

    table.cell(rowspan: 2)[*ticketdb* \ ticket-supplier],
    [`ticket_product`],
    [`id` varchar(16) *PK* · `name` · `description` · `price` decimal · `stock` int],
    [`ticket_reservation`],
    [`id` varchar(36) *PK* (UUID) · `product_id` varchar(16) · `quantity` int ·
     `status` enum (PENDING / CONFIRMED / CANCELLED) · `created_at` timestamp ·
     `expires_at` timestamp (TTL)],
  ),
) <fig-schema>

Three decisions shaped this model. The first is *domain ownership*: each company is the sole
writer of its own catalog and stock, which is what makes the suppliers genuinely independent
services rather than tables in a shared database. The second is that the broker order doubles
as the *transaction log*: the order-level and item-level `status` fields are committed to the
database at every protocol step, and that durable trail is what makes crash recovery possible
(@sec-tx). The third is *snapshotting*: an order item copies the product's name and price at
order time, so a later catalog change never rewrites a customer's historical order. Payment is
simulated — the broker stores only a cardholder name and the last four digits, all validated
server-side, with no real card protocol behind them.

= Ordering as a distributed transaction <sec-tx>

== The protocol

Placing an order runs a two-phase, coordinator-driven protocol in
`AtomicOrderService.placeOrder`. The order row moves through

#align(center, box(inset: (y: 6pt))[
  `CREATED` → `RESERVING` → *`RESERVED` (commit point)* → `CONFIRMING` → `SUCCEEDED`
])

with `FAILED` as the only other terminal state. @fig-2pc traces one successful order.

#figure(
  caption: [Protocol trace of a successful two-item order. Every state change is its own
    committed database write, so the order row always reflects the true protocol state.],
  table(
    columns: (auto, 1fr),
    align: (left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Message*], [*Effect*]),
    table.cell(colspan: 2, fill: luma(243))[*Claim* — the status CAS `CREATED → RESERVING`
      admits exactly one executor (see below)],
    [broker → ticket-supplier: \ `reserve(T-001, 2)`], [hold taken atomically, stock −2,
      TTL stamped; ← `201 {reservationId}`; item persisted `RESERVED`],
    [broker → food-and-beverages: \ `reserve(F-001, 1)`], [same; all items held → order
      durably *`RESERVED`* — the commit point],
    table.cell(colspan: 2, fill: luma(243))[*Phase 2* — order `CONFIRMING`; from here the
      protocol only rolls _forward_],
    [broker → ticket-supplier: \ `confirm(reservationId)`], [hold becomes a permanent sale
      (idempotent); item `CONFIRMED`],
    [broker → food-and-beverages: \ `confirm(reservationId)`], [same; order `SUCCEEDED`],
  ),
) <fig-2pc>

- *Phase 1 — reserve (the "vote").* While reserving, the order is still undecided, so *any*
  failure rolls *back*: already-taken holds are cancelled (compensation, in reverse order) and
  the order is marked `FAILED`.
- *Commit point.* Once every item is held, the order is durably `RESERVED`. This is the
  decision to commit; past this point the protocol only rolls *forward*.
- *Phase 2 — confirm (the "commit").* Each confirm turns a hold into a permanent sale. If a
  confirm fails midway, the order is *not* aborted — some items may already be sold — but left
  durably `CONFIRMING` for a retrier to finish (confirm is idempotent, so replays are safe).

This is best described as a *Try–Confirm/Cancel (TCC)* protocol — two-phase commit with a
durable commit point, where a supplier's hold is a *committed local transaction* rather than a
lock held open, and abort is achieved by *compensation*. That keeps the suppliers autonomous
and non-blocking, at the cost of global isolation between reserve and confirm (@sec-acid).

== One order, one executor

An order can have several potential executors: the customer's request thread (default
profile), the queue listener (`async`), and the recovery sweep. Starting an order is therefore
an *atomic status CAS* in the database — `UPDATE … SET status='RESERVING' WHERE id=? AND
status='CREATED'` — and only the single caller that wins the update runs the protocol;
everyone else sees a no-op. This is what makes retries and crash recovery safe by
construction: phase 1 can never run twice for the same order, so stock is never reserved
twice.

== Crash recovery

A recovery sweep runs every 60 seconds (not only at startup). It resumes every order left
mid-protocol, applying the classic coordinator rule: `RESERVED`/`CONFIRMING` roll *forward*
(confirm the remaining holds), `RESERVING` rolls *back* (cancel the holds taken so far, order
`FAILED`). The sweep only touches orders *older than five minutes* — younger ones may still be
executing in a live request, and recovering those concurrently would double-process them.

Two backstops cover what the broker alone cannot see:

- *Reservation TTL.* If the broker dies after a supplier took a hold but before the
  `reservationId` was persisted, that hold is recorded nowhere the broker can find. Each
  supplier therefore expires unconfirmed holds itself: a cleanup task cancels `PENDING`
  reservations past their `expiresAt` (default 60 minutes — deliberately far above the
  15-minute completion window, because a hold may legitimately be confirmed that late and an
  earlier expiry would void a committed order's hold).
- *Failure classification.* Supplier failures carry a reason: a 4xx answer is a *permanent
  refusal* (out of stock, unknown or already-cancelled hold) that no retry can change, while
  timeouts and 5xx are *transient*. Retry logic acts on the class, not blindly (@sec-fault).

== Asynchronous ordering (Level 2)

Under the `async` profile the order form does not block: the controller saves the order,
enqueues its id on an embedded ActiveMQ Artemis queue, and redirects immediately; the order
page refreshes itself while a listener completes the order in the background. The customer can
leave the shop — the listener keeps retrying with backoff for up to *15 minutes*, a window
anchored to the order's creation time, so redeliveries and broker restarts never extend it. A
supplier that is merely unreachable does not fail the order: the holds are compensated and the
order returns to `CREATED` for the next attempt within its window.

At the deadline the rule is asymmetric, and deliberately so: an order that never reached its
commit point is failed (nothing is held anywhere, so the abort is safe); an order *past* its
commit point is never aborted — the recovery sweep keeps rolling it forward even beyond the
window, because a committed sale must not be discarded.

= ACID analysis <sec-acid>

#figure(
  caption: [ACID properties: what guarantees each one, and what threatens it.],
  table(
    columns: (auto, 1fr, 1fr),
    align: (left + horizon, left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Property*], [*Threats*], [*Mechanism*]),
    [Atomicity], [a supplier fails mid-order; the broker crashes between phases; two executors
      run the same order], [durable commit point + compensation before it, roll-forward after
      it; single-executor claim (CAS); idempotent confirm/cancel],
    [Consistency], [overselling under concurrency; orders without address or payment],
      [stock is decremented under a pessimistic row lock (SQL) or an atomic conditional update
      (MongoDB); server-side validation gates the order before any hold is taken],
    [Isolation], [two customers race for the last unit], [each supplier operation is a
      serialized local transaction; a reserve immediately removes units from availability],
    [Durability], [process restarts], [every protocol step is its own committed write, on the
      broker (order + item status) and on the suppliers (holds and sales)],
  ),
) <tab-acid>

*Where ACID can still break.* Three honest limitations remain, and they are fundamental
trade-offs rather than oversights:

+ *The reservation TTL is an in-doubt-participant heuristic.* A hold is a supplier's "yes"
  vote; expiring it is the participant unilaterally aborting while in doubt. If the broker
  stays down longer than the TTL *after* the commit point, a confirm can later be refused
  because the hold expired. The broker then distinguishes two cases: if *nothing* of the order
  was sold yet, it aborts cleanly (externally invisible despite the commit decision); if *part*
  of the order is already a permanent sale, atomicity is genuinely broken — the order stays
  `CONFIRMING` and is flagged loudly for manual reconciliation. This is the classic 2PC
  in-doubt window; we chose a long TTL (60 min ≫ the 15-min completion window) to make the
  case practically unreachable, and an honest escalation when it happens anyway.
+ *MongoDB offers no cross-document atomicity here* (@sec-nosql): a crash between the two
  writes of a reserve/cancel can leak a held unit. It is always the safe direction — it can
  never oversell — and the TTL cleanup reclaims leaked holds.
+ *No global isolation.* Between reserve and confirm, a manager can observe an order in an
  intermediate state. For a webshop this is acceptable — and inherent to non-blocking TCC.

= Coping with supplier failures <sec-fault>

A broker that falls over whenever a supplier does would be useless, so supplier failures are
*inputs to the protocol*, not emergencies. Every supplier error is classified and handled by
class:

#figure(
  caption: [Failure classification and what each class triggers.],
  table(
    columns: (auto, 1fr, 1fr),
    align: (left + horizon, left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Class*], [*Meaning*], [*Reaction*]),
    [Permanent refusal \ (HTTP 400/404/409)], [the supplier answered and said _no_: out of
      stock, unknown product, hold no longer confirmable], [fail fast — phase 1 compensates and
      the order `FAILED`s; a refused confirm triggers the in-doubt handling of @sec-acid],
    [Transient \ (timeout, connect error, 5xx)], [no usable answer; retrying may succeed],
      [phase 1: compensate, then retry within the completion window (`async`) or fail
      (synchronous); phase 2: stay `CONFIRMING`, the sweep retries every minute],
  ),
) <tab-failures>

Concretely, the broker survives the malicious cases the assignment names:

- *Unresponsive supplier.* All supplier calls carry 5 s connect / 10 s read timeouts, so a
  hung supplier becomes an ordinary transient failure instead of a blocked thread.
- *Malformed response.* A reserve answer without a reservation id and catalog items with
  missing fields are guarded explicitly; transport-level garbage surfaces as a client
  exception — all of it is caught and classified, never propagated raw.
- *Browsing degradation.* On the read path the broker catches supplier errors per catalog
  section and renders that section empty (logged); one dead supplier never breaks the page.

The three demonstration scenarios required by the assignment:

#figure(
  caption: [The required failure scenarios and where each is demonstrated
    (JUnit + the end-to-end scripts in `tests/`).],
  table(
    columns: (auto, 1fr, auto),
    align: (left + horizon, left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Scenario*], [*Behaviour*], [*Demonstrated by*]),
    [Supplier crashes mid-order], [phase 1: compensate + `FAILED`; phase 2: stays `CONFIRMING`,
      rolled forward once the supplier returns], [`AtomicOrderServiceTests`, \ `test-4`, `test-5`],
    [Network failure], [timeouts turn it into the same transient class as a crash; under
      `async` the order keeps retrying inside its window], [`OrderQueueListenerTests`, \ `test-5`],
    [Broker crashes before confirm], [the durable order status + the recovery sweep finish (or
      undo) the protocol after restart], [`OrderRecoveryTests`, \ `test-6`],
  ),
) <tab-scenarios>

*How severely may a supplier misbehave?* Crashes, hangs, connection refusals, 5xx storms and
malformed bodies all degrade the shop (empty catalog section, failed or delayed orders) but
never crash or block the broker. The one thing a supplier can still do to hurt is *lying
successfully* — confirming a hold and then not delivering, which no protocol on this layer can
detect; and the TTL edge case of @sec-acid, which is contained and flagged.

= Security and access control <sec-security>

Customers never need an account. They browse, order and "pay" anonymously, providing only a
delivery address, a cardholder name and the last four card digits — validated server-side,
with no real payment protocol behind them. Managers are the only authenticated humans, and the
only thing authentication buys them is `GET /manager/orders`: the overview of *all* customers'
orders with their states.

*Default profile.* Enforcement is one Spring Security rule:
`requestMatchers("/manager/**").hasRole("MANAGER")` with `anyRequest().permitAll()` and HTTP
Basic. Credentials come from configuration (BCrypt-hashed in memory, overridable per
environment). Tampering with the URL without the role yields `401`/`403` — covered by tests.
CSRF stays enabled for the browser forms. Order pages are reachable only via their unguessable
UUID, a deliberate capability-URL design for account-less customers.

*`auth0` profile (Level 2).* Both humans and machines authenticate against an external
identity provider:

- *Humans* log in via Auth0 (OIDC). Roles travel in a custom claim on the ID token; the broker
  maps them to authorities, so `/manager/**` still requires `MANAGER` — now backed by an IdP
  instead of a local user. Logout round-trips through Auth0's logout endpoint.
- *Machines:* the broker obtains a client-credentials (M2M) token from Auth0, caches it until
  expiry, and presents it as a bearer token on every supplier call. The suppliers are OAuth2
  resource servers that validate the token's *issuer and audience* — they no longer accept
  orders from arbitrary HTTP clients on the internet, which answers the assignment's B2B
  security question.

@fig-auth shows both halves: the human OIDC login on the left, and the machine-to-machine
token the broker carries to the suppliers on the right.

#figure(
  caption: [Authorization under the `auth0` profile. Left: humans authenticate to the broker
    with the OIDC authorization-code flow. Right: the broker authenticates to the suppliers
    with a cached machine-to-machine JWT, which each supplier validates as an OAuth2 resource
    server. The default profile uses neither — anonymous shop, HTTP-Basic manager, open B2B API.],
  block(width: 100%)[
    #set align(center)
    #grid(columns: (1fr, 1fr), gutter: 16pt,
      [
        #text(weight: "bold", size: 9pt)[Human access — OIDC]
        #v(5pt)
        #dnode("Customer / manager browser", width: 100%)
        #adown(label: [1 · redirect to log in])
        #dnode("Auth0 — identity provider", width: 100%, fill: luma(245), edge: luma(120),
          body: [authenticates the human; issues an ID token carrying a roles claim])
        #adown(label: [2 · ID token (roles claim)])
        #dnode("Broker — OIDC client", width: 100%,
          body: [maps the claim to authorities; `/manager/**` requires role `MANAGER`,
            everything else stays anonymous])
      ],
      [
        #text(weight: "bold", size: 9pt)[Service-to-service — M2M JWT]
        #v(5pt)
        #dnode("Broker — M2M client", width: 100%, body: [obtains the token via `Auth0TokenService`])
        #adown(label: [1 · client-credentials grant \ (client id + secret, audience)])
        #dnode("Auth0 — token endpoint", width: 100%, fill: luma(245), edge: luma(120),
          body: [issues a signed JWT access token])
        #adown(label: [2 · JWT, cached until expiry])
        #dnode("Broker → supplier call", width: 100%,
          body: [`Authorization: Bearer <JWT>` on every reserve / confirm / cancel])
        #adown(label: [3 · validate signature, issuer, audience])
        #dnode("Supplier — OAuth2 resource server", width: 100%,
          body: [serves on a valid token; otherwise `401`])
      ],
    )
  ],
) <fig-auth>

= SQL versus NoSQL: the MongoDB experience <sec-nosql>

To compare SQL and NoSQL on the same problem, the ticket supplier ships a `mongo` profile
(Spring Data MongoDB) beside its default relational stack, behind the *same* REST contract.
The interesting part is what the relational version got for free and MongoDB did not — the
query and consistency limitations we actually hit:

- *No multi-statement ACID transaction* (without running the server as a replica set) *and no
  `SELECT … FOR UPDATE`.* Every state change is therefore a *single-document atomic
  `findAndModify` compare-and-set*: reserve is a conditional decrement (`stock ≥ qty`),
  confirm/cancel are transitions gated on `status == PENDING`. Concurrent or duplicate calls
  match nothing and no-op — overselling-safe and idempotent without locks.
- *Cross-document atomicity is lost.* Reserve and cancel each touch two documents (product and
  reservation); a crash between the writes can leak a held unit — in the safe direction only,
  and the TTL cleanup reclaims it (@sec-acid).
- *No joins or foreign keys.* The reservation→product link is an id resolved by a second
  query; referential integrity becomes the application's responsibility. Ad-hoc reporting
  queries that are one `JOIN` in SQL become aggregation pipelines or application-side loops.

The trade-off in one line: the relational backend gives true cross-entity atomicity through
locks and `@Transactional`; MongoDB gives lock-free, idempotent single-document operations
that scale well but push multi-document consistency back onto the application.

= Running in the cloud <sec-cloud>

== Migration pitfalls

Moving from the laptop to Azure surfaced several issues, most isolated to configuration:

- *Database engine swap.* Local development uses passwordless MySQL on `localhost`; Azure uses
  Azure Database for PostgreSQL — different driver, enforced TLS, credentials via environment
  variables, server-firewall rules.
- *Networking.* Services are no longer co-located: the broker reaches the suppliers by public
  address across regions, which requires network-security-group rules and adds cross-region
  latency to every reserve and confirm. The supplier timeouts (@sec-fault) double as the
  guard against a peer region misbehaving.
- *Schema management.* `ddl-auto=update` is convenient but risky against a managed database; a
  production setup would use migrations (e.g. Flyway) or `validate`.
- *Free-tier limits* constrain VM size, database SKU and connection counts, so cold starts and
  connection caps must be planned for.

== Cost analysis

Costs split *per administrative domain*: each company pays only for its own compute and
database. Each account needs one small compute instance plus one managed database.

#figure(
  caption: [Monthly cost per company (region-dependent; verify on the Azure pricing
    calculator).],
  table(
    columns: (auto, 1fr, auto, auto),
    align: (left + horizon, left + horizon, left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Company*], [*Resources*], [*Free tier*], [*Paid estimate*]),
    [Broker], [B-series VM + PostgreSQL Flexible Server], [≈ €0], [€20–25],
    [food-and-beverages], [B-series VM + PostgreSQL Flexible Server], [≈ €0], [€20–25],
    [ticket-supplier], [B-series VM + PostgreSQL *or* MongoDB], [≈ €0], [€20–25],
    [*Total*], [], [*≈ €0*], [*€60–75* #todo("replace with actual spend")],
  ),
) <tab-cost>

== Provider lock-in

The tie-in with Azure is *low*: plain Spring Boot with JDBC/JPA (and optionally the MongoDB
API), no Azure SDK in the source. Even the Level-2 additions stay portable by construction —
the order queue is an *embedded* Artemis broker (no Azure Service Bus dependency) and the IdP
is Auth0, which is cloud-neutral. Migrating to AWS or GCP means pointing the `azure`-style
profile at that provider's managed PostgreSQL and redeploying the same JARs on its compute —
no core code changes.

= Testing and demonstration <sec-testing>

*Automated tests.* The broker's JUnit suite runs against the in-process `stub` suppliers and
covers the protocol exhaustively: the 2PC happy path across all three product types, phase-1
rollback with stock restoration, the phase-2 roll-forward rule (a committed order is never
rolled back), crash recovery in both directions, the single-executor claim, re-entry safety
(an in-flight order is never reserved twice), the deadline rules of the async listener,
refused-confirm handling in both arms, transient-failure retry, catalog degradation, manager
authentication and form validation. Each supplier module has service- and web-contract tests,
including the TTL cleanup (expired holds are released; confirmed sales never expire). All
suites run with `./mvnw test` per module.

*End-to-end scripts.* `tests/` contains nine bash scripts that drive the *deployed* system
(addresses in `tests/config.sh`):

#figure(
  caption: [End-to-end test scripts.],
  table(
    columns: (auto, 1fr),
    align: (left + horizon, left + horizon),
    inset: 5.5pt,
    table.header([*Script*], [*What it proves*]),
    [`test-1-connectivity`], [all three services and their catalogs are reachable],
    [`test-2-happy-path-2pc`], [reserve→confirm across both suppliers; stock consumed once],
    [`test-3-out-of-stock`], [a 409 vote aborts the order; no stock moves],
    [`test-4-2pc-rollback`], [one supplier failing mid-order releases the other's hold],
    [`test-5-supplier-crash`], [supplier killed mid-order: broker compensates / degrades],
    [`test-6-broker-recovery`], [broker killed before confirm: recovery finishes the order],
    [`test-7-idempotency`], [duplicate confirm/cancel are safe no-ops],
    [`test-8-manager-dashboard`], [managers authenticate; anonymous access is rejected],
    [`test-9-broker-end-to-end`], [a full customer journey through the real GUI endpoints],
  ),
) <tab-scripts>

*Demo.* The defense demo follows @tab-scenarios: kill a supplier mid-order (`test-5`), kill
the broker between reserve and confirm and restart it (`test-6`), and place an order with a
supplier down under the `async` profile to show the 15-minute background completion.
#todo("attach demo logs / screenshots of the three scenarios")

= Team contributions <sec-team>

#figure(
  caption: [Team roles, contributions and hours, as required by the assignment.],
  table(
    columns: (auto, auto, 1fr, auto),
    align: (left + horizon, left + horizon, left + horizon, left + horizon),
    inset: 6pt,
    table.header([*Name*], [*Role(s)*], [*Key contributions*], [*Hours*]),
    [Sodir YUKSEL], [#todo("role")], [#todo("contributions")], [#todo("h")],
    [Dimitrios KYRANOS], [#todo("role")], [#todo("contributions")], [#todo("h")],
    [Demirhan YASAR], [#todo("role")], [#todo("contributions")], [#todo("h")],
    [Celal Emre ERKAN], [developer], [food and beverages supplier code and access control ], [15],
  ),
) <tab-team>

= Conclusion <sec-conclusion>

The project delivers what the assignment asks at the basic level and beyond: a composite
package whose purchase is a genuine distributed transaction across two independent supplier
companies, coordinated by a broker with a durable commit point, compensation before it,
roll-forward after it, and crash recovery that survives killing any process mid-protocol.
Suppliers never oversell; a hung or malformed supplier degrades the shop instead of breaking
it; managers authenticate for the global order view; and the whole system spans three Azure
accounts, regions and databases — three real administrative domains.

The Level-2 features are implemented as opt-in profiles on the same codebase: Auth0 replaces
the local login for humans and adds machine-to-machine JWTs between broker and suppliers, and
queued ordering completes orders in the background within a 15-minute window while respecting
ACID at the deadline. The ticket supplier's MongoDB profile demonstrates the same supplier
contract on NoSQL and made the SQL-versus-NoSQL trade-offs concrete.

The honest limitations are documented where they live: the reservation TTL is an
in-doubt-participant heuristic whose pathological case ends in flagged manual reconciliation
rather than silent inconsistency, and MongoDB's missing cross-document atomicity leaks only in
the safe direction. Both are inherent trade-offs of non-blocking commitment protocols — knowing
*where* the guarantees end proved as instructive as building them.
