# One byte decides: measuring Valhalla on JDK 28

*Notes from a few days of running JEP 401 value classes against a heap profiler, a JIT, and several
libraries that were not expecting them.*

**Code:** [github.com/LeszekLeszczynski/confitura2026](https://github.com/LeszekLeszczynski/confitura2026) — 25
runnable demos, each printing the numbers quoted here.

> **Disclaimer.** This article is AI-generated, and so are the experiments it describes. They were written,
> run and measured by an AI, guided by a curious *homo sapiens* asking the questions — including the awkward ones
> that sent several confident explanations back to the drawing board. Every number below came out of an actual
> run on the machine, not out of a language model's memory; where a claim could not be verified, it is labelled
> as unverified. The design of the experiments, and the interpretation, deserve your scepticism as much as any
> other blog post — more, perhaps, because a plausible-sounding wrong explanation is exactly what this technology
> is best at. The demos are there so you can check.

Everything below was measured on **JDK 28-ea build 28-ea+14** with `--enable-preview`, on an arm64 Mac with
compact object headers on by default (so an object header is 8 bytes, not 12). Every number is reproducible from
the demo repo that accompanies these notes; the demo name is given with each result. Where a measurement
contradicted what I expected, I have kept both the expectation and the correction, because the corrections turned
out to be the interesting part.

A warning about early access: the layout rules below are what *this build* does. Several of them are tuning
decisions, not specification, and they will move.

---

## What this is about

A Java object has always been two things at once: the data you care about, and an *identity* — an address on the
heap that you can lock on, weakly reference, and compare with `==`. You pay for that identity whether you use it
or not: a header on every object, a pointer to reach it, and a cache miss when you follow the pointer.

**JEP 401** lets you say that a class has no identity: `value class Point { int x; int y; }`. Its fields are
final, you cannot lock on it, and two instances with equal fields are indistinguishable — there is no test that
tells them apart. That last part is the whole point. If nothing can tell two equal `Point`s apart, the JVM is free
to copy one, inline it into its owner, or take it apart and put it back together, without proving anything to
anybody. Two optimisations follow.

**Flattening** puts the data where the pointer used to be:

```
   identity Point                              value Point, flattened
   ──────────────────────────────              ──────────────────────────────
   Line                    16 B                RecordLine              32 B
     header                 8                    header                 8
     start  ──▶ ┌─────────────────┐              start.x                4
     end    ──▶ │ Point     16 B  │              start.y                4
                │   header    8   │              (null marker)          1
                │   x         4   │              end.x                  4
                │   y         4   │              end.y                  4
                └─────────────────┘              (null marker)          1
   ──────────────────────────────              ──────────────────────────────
   3 objects, 48 bytes, 2 hops                 1 object, 32 bytes, 0 hops
```

The same thing happens to arrays, and this is where it matters most — a million elements instead of two:

```
   LocalDate[3], references                    LocalDate[3], flat
   ┌─────┬─────┬─────┐                         ┌───────────┬───────────┬───────────┐
   │  ●  │  ●  │  ●  │  4 B each               │ y  m d  ▫ │ y  m d  ▫ │ y  m d  ▫ │
   └──┬──┴──┬──┴──┬──┘                         └───────────┴───────────┴───────────┘
      ▼     ▼     ▼                              8 bytes per element, no objects
   ┌─────┐┌─────┐┌─────┐                         (▫ = the null marker, 1 byte)
   │ hdr ││ hdr ││ hdr │  16 B each
   │ y m ││ y m ││ y m │
   │ d   ││ d   ││ d   │
   └─────┘└─────┘└─────┘
   4 + 16 = 20 bytes per element
```

**Scalarization** goes further and removes the object even from a single method. If a value never has to be an
object, the JIT keeps its fields in registers and local variables:

```
   what you wrote                              what the JIT compiles
   ─────────────────────────────               ─────────────────────────────
   Point s = new Point(0, 0);                  int s_x = 0, s_y = 0;
   for (...)                                   for (...)
       s = s.plus(p);                              { s_x, s_y } = plus(s_x, s_y, p_x, p_y);
   return s;                                   return { s_x, s_y };

   an object per iteration                     no object, ever
```

The JVM has always attempted this for ordinary objects through *escape analysis* — but there it must first prove
the object never escapes the method, and that proof breaks easily: pass it to a method that was not inlined,
return it, or store it in a field, and the allocation comes back. For a value object there is nothing to prove,
so the fields can travel through calls in registers, and the object is assembled only if someone genuinely needs
a reference.

Those are the promises: **fewer objects, smaller objects, fewer pointer hops, and hot code that allocates
nothing.** The rest of this article is what actually happens when you measure them.

## 1. The rule nobody puts on a slide

Value classes get flattened — stored inline, no header, no pointer — but only sometimes. The condition on this
build is narrow enough to state exactly:

> A value object in a place that can be reassigned — a field of an ordinary class, or an array element — is
> flattened only if **one element, payload plus a one-byte null marker, fits in a single atomic 64-bit access.**

So: payload ≤ 7 bytes for a nullable field or `new T[n]`, ≤ 8 if the slot cannot be null. The reason is
concurrency. Two threads writing the same slot must never produce a torn value, and a single aligned 64-bit write
is the only thing the hardware gives you for free.

That one byte produces the sharpest result I got. Two classes with the same shape — a holder with two small
immutable fields:

```java
class Period { LocalDate start; LocalDate end; }   // LocalDate: int year, byte month, byte day = 7 bytes

value class Point { int x; int y; }                // 8 bytes
class Line { Point start; Point end; }
```

| holder | fields flat? | holder | + fields = total | objects |
|---|---|---|---|---|
| `Period`, no preview | no | 16 B | 48 B | 3 |
| **`Period`, preview** | **yes** | **24 B** | **24 B** | **1** |
| `Line` (value `Point`), preview | no | 16 B | **64 B** | 3 |
| `IdentityLine` (plain `class Point`) | no | 16 B | 48 B | 3 |
| `record RecordLine(Point, Point)`, preview | **yes** | **32 B** | **32 B** | **1** |

*(demo 22, `PeriodVsLine` / `ValuePeriodVsLine`)*

`Period` halves and collapses to a single object **with no change to your code** — `LocalDate` became a value
class in the JDK and its payload happens to be 7 bytes. `Line`, written deliberately with a value class, does not
move: 8 + 1 does not fit 8. And it is worse than that, which brings us to the next finding.

## 2. `value` can make things bigger

When a value object cannot be flattened it lives on the heap as a *buffered* copy — and a buffered value carries
the null marker byte with it, because its layout is deliberately identical to a nullable flat slot (that makes
copying between heap and slot a plain memory copy). An identity record has no marker. So:

| payload | buffered value | identity record | extra |
|---|---|---|---|
| 7 bytes | 16 B | 16 B | 0 |
| **8 bytes** | 24 B | 16 B | **+8** |
| 12 bytes | 24 B | 24 B | 0 |
| **16 bytes** | 32 B | 24 B | **+8** |
| **64 bytes** | 80 B | 72 B | **+8** |

*(demo 07b, `NullMarker`)*

The penalty is never partial: it is exactly 8 bytes, and it lands precisely when the payload fills its last
8-byte word — which is the common case for `(int, int)`, `(long, long)`, `(long, int, int)`. Real domain types.

Hence `Line` at 64 bytes against the identity version's 48. Turning a `record Point` into a `value record Point`
made every line **33 % larger**. Nothing warns you.

The escape hatch is in the table in section 1: **a record component is a strict field** — assigned before
`super()` and never again — so no read can race a write, the JVM uses a non-atomic layout, and the size limit
disappears. I flattened payloads up to 256 bytes that way. Plain `final` does *not* count: javac marks it neither
strict nor trusted, because reflection can still rewrite it.

The practical shape of the rule: **value classes want to live in records.** The combination the language has been
nudging people toward since JDK 16 is exactly the one the JVM can flatten without limits.

## 3. Where the savings actually are

Two models, the same three-way treatment: mutable classes with wrapper types (A), immutable records (B), records
plus value records for the small types (C).

**An invoice** — customer with address, country code and phone, plus 10 line items each with quantity, unit price,
tax rate and delivery date:

| bytes per invoice | plain (nothing flattened) | preview |
|---|---|---|
| **A** classic classes | **1,153** | 1,059 (−8 %) |
| **B** records | 1,108 | 948 (−18 %) |
| **C** records + value records | — | **660 (−43 % vs. plain A)** |

*(demo 19, `InvoiceModels` / `ValueInvoiceModels`)*

The whole invoice becomes **96 bytes in one object**: invoice → customer → address → country code, four levels of
value records all inline, because each level is a record component. Only the `String`s and the `List` stay
references.

**An article** — the counter-example, a model that is almost entirely text: slug, title, summary, author with bio,
4 tags, 5 sections of prose:

| model | structure / article | total / article | strings' share |
|---|---|---|---|
| **A** classic classes | 324 B | 2,980 B | 89 % |
| **B** records | 324 B | 2,980 B | 89 % |
| **C** records + value records | **320 B** | 2,976 B | 89 % |

*(demo 20, `ArticleModels` / `ValueArticleModels`)*

**Four bytes. 0.13 %.** The author record does flatten into the article, but four references inline instead of
four references behind a pointer saves only the author's header, and alignment eats half of that; the sections sit
in a `List`, so they stay buffered objects; and 2,656 bytes of `String` + `byte[]` per article are untouchable.

Same tools, same keyword, opposite outcome. Valhalla pays in proportion to how much of your data is small and
primitive-like — amounts, dates, codes, ids, coordinates. Text-heavy models see nothing, because their bytes were
never in the objects to begin with.

One limit worth knowing before you plan a migration: **collections and arrays of your domain objects do not
flatten.** A `List` is an `Object[]` underneath and can never be flat; and a `VLineItem[]` is not flat either,
because one element is ~40 bytes, far over the array limit. The flattening happens *inside* each element.

## 4. Scalarization: the part that is actually fast

Flattening is about data on the heap. Scalarization is about code: a value object has no identity, so the JIT may
keep its fields in registers and never build the object — and unlike escape analysis for ordinary objects, that
survives a method call, because the compiled method passes and returns the fields instead of a pointer.

Same loop, `s = s.plus(new Point(xs[i], ys[i]))`, 10,000 times. The second column is a forked JVM where `plus` may
not be inlined, so escape analysis has nothing to work with:

| | inlining allowed | dontinline |
|---|---|---|
| identity record `Point` | 16.00 B/op | 32.00 B/op |
| **value record `Point` (8 B)** | **0.00** | **0.00** |
| **value record `Big` (32 B)** | **0.00** | **0.00** |
| value `Point` through an `Object` parameter | 0.00 | **48.00 B/op** |

*(demo 23, `Scalarization`)*

Three things at once. The identity record allocates even *with* inlining — escape analysis removed only one of the
two objects per iteration, so this is not a straw man. The value record allocates nothing even across a
non-inlined call. `Big` proves there is no size limit here: the stack has no races between threads, so the
32-byte payload that could never flatten in a field scalarizes completely.

And it is faster, not merely leaner. One loop — a pricing calculation over 10,000 risks — written three ways:

| benchmark | time | allocation |
|---|---|---|
| `Money` as a value class | **13.57 µs/op** | **0.09 B/op** |
| the same model as an identity record | 22.77 µs/op | 160,000 B/op |
| no domain type at all, bare `long` | 12.88 µs/op | 0.08 B/op |

*(demo 24, `ScalarizationSpeed`, JMH with `-prof gc`)*

**1.7× faster than the identity version, and within 5 % of raw `long`s.** That kills the oldest argument against
domain types in hot code: *"we keep amounts in `long` because a wrapper costs too much."* It no longer does.

## 5. The stream trap

The same `Money`, the same arithmetic, two ways of summing a list:

```java
for (var m : amounts) total = total.plus(m);          // concrete type
amounts.stream().reduce(Money.ZERO, Money::plus);     // BinaryOperator<T> → Object
```

| | time | allocation |
|---|---|---|
| `for` loop | **5.17 µs/op** | **0.03 B/op** |
| `stream().reduce(...)` | 22.43 µs/op | 240,120 B/op |
| `stream().map(...).reduce(...)` | — | 480,000 B/op |
| `stream().mapToLong(...).sum()` | — | 0.02 B/op |

*(demo 24)*

**4.3× slower, and a quarter of a megabyte of garbage per pass.** `BinaryOperator<T>` erases to `Object`, so every
intermediate `Money` must be materialized — the same effect as the last row of the table in section 4. This is the
only allocation I measured that the JIT cannot remove at any tier: it is structural, not circumstantial. Escaping
to `mapToLong` gets back to zero, because the value never crosses a generic boundary.

So, for now: **in hot code with value classes, prefer the loop.** Not folklore — a 4× difference in wall-clock
time. It is also a concrete reason why "specialised generics" is the next Valhalla milestone rather than a nicety.

## 6. The warm-up cliff, and the thing you cannot write around

A flattened field holds no object, so asking for one — `risk.base()` where `base` is a flat `Money` — has to build
it. Until it doesn't. The same loop, batch after batch, nothing changing but how often it has run:

```
batch  1  (calls   1- 20)   24.01 B per read
batch  2  (calls  21- 40)   24.01 B per read
batch  3  (calls  41- 60)    0.00 B per read   <- C2 has compiled it
...
batch 12  (calls 221-240)    0.00 B per read
```

*(demo 25, `WarmUpCost`)*

So the honest formulation is neither "flattening is free" nor "reads cost an allocation": **a read costs one
buffered value until the method is C2-compiled, and nothing at all afterwards.** Here that is the first ~40
passes — about 10 MB of young-gen garbage once per JVM — against 160,000 bytes and 10,000 objects saved for as
long as the data lives.

Where the trade goes the wrong way: code that never gets hot. CLI tools, serverless cold starts, start-up paths,
unit tests.

And there is nothing to write around it. I tried hiding the read inside the class, so the `Money` never crosses a
class boundary and the method hands out a `long`:

| | C2 | C1 only |
|---|---|---|
| `r.base().cents()` | 0.00 B | 24.00 B |
| `r.getCents()` — `return base.cents()` inside `Risk` | 0.00 B | 24.00 B |

The bytecode explains it: `getfield base` on a flattened field produces a `Money` *reference* by definition,
wherever it appears. C1 implements that literally; C2 tracks the fields and never builds the object. The boundary
that decides the cost is not the class, the method or the accessor — it is the compiler tier, and no amount of
encapsulation moves it.

## 7. Things that break

None of these are hypothetical; all were run.

**Apache Commons Lang 3.20.0** — the current release — throws on any object with a boxed field:

```
ReflectionToStringBuilder.toString(order)   -> IdentityException: java.lang.Integer is not an identity class
new ToStringBuilder(this).append("id", id)  -> IdentityException: java.lang.Long is not an identity class
```

*(demo 15, `CommonsLangToString`)*

`ToStringStyle`'s cycle-detection registry is a `ThreadLocal<WeakHashMap<Object, Object>>`, and `register()` puts
every visited value into it, so `WeakHashMap.put` → `WeakReference` → `Objects.requireIdentity` throws. It is
**not** about reflection: the IDE-generated hand-written `toString()` fails identically, and only the primitive
overloads and `String` survive. A `WeakHashMap` used as a "have I seen this object" set — a perfectly reasonable
pattern for 25 years — is now a runtime exception whenever a boxed number passes through it.

**Ehcache 3.12.0** does something worse: it fails silently. Its bundled `sizeof` walks the object graph with an
`IdentityHashMap` of visited objects, and equal value objects collapse to one:

| a `List<Integer>` of 1000 equal values | |
|---|---|
| really on the heap | 20,040 B (24 + 4,016 + 1,000 × 16) |
| reported, no preview | 20,040 B ✓ |
| **reported, preview** | **4,056 B** — it counted one `Integer` |

Configure `heap(200, MemoryUnit.KB)` and put 2,000 entries: the cache retains **91 entries plain and 312 under
preview**, holding 636 KB against a 200 KB budget — **311 %** — while its own metrics report it as full. *(demo 16,
`CacheSizing`)*

**Identity as a security mechanism.** An object-capability registry — a token is valid because the vault handed
*that object* out, kept in an identity set — survives a forged token when the token is an identity record
(`SecurityException`) and accepts it when someone changes the record to a `value record`: `new ValueToken(1)` is
`==` to the issued one. Unforgeability of a reference was the whole mechanism, and one keyword deleted it, with no
failing test. *(demo 14, `IdentityAssumptions`)*

**And one door left open.** A value record with a validating constructor cannot normally be fabricated — the
`ValueClass` array factories demand an explicit initial value, javac requires strict fields to be definitely
assigned before `super()`, the verifier rejects bytecode that does otherwise, and both `ObjectInputStream` and
`ReflectionFactory` refuse classes with strict fields. But `sun.misc.Unsafe.allocateInstance(Money.class)` returns
`Money[amount=0, currency=0]` with no flags required, and the constructor counter says it never ran. *(demo 12,
`DefaultValueBypass`)* The same call has always bypassed constructors for identity classes; what is new is that
the fabricated value is indistinguishable from a real one, and can sit in a flat null-restricted field where
`null` would previously have made the omission visible.

## 8. What I could not explain

A value model serialized to JSON is a wash at best:

| bytes allocated per invoice | build model | Jackson databind | hand-written streaming |
|---|---|---|---|
| `ClassicInvoice` | 1,055 | 3,672 | 1,696 |
| `VInvoice` | **656** (−400) | **4,069** (+400) | **2,839** (+1,140) |

*(demo 21, `JacksonInvoice`)*

The 400 bytes saved in the model come back during serialization, and a hand-written streaming serializer — no
reflection, all accessors C2-inlined, verified with `-XX:+PrintInlining` — is worse still. The obvious
explanations are all wrong, and each was tested:

| hypothesis | test | result |
|---|---|---|
| erasure: values pass through `Object` | the generator takes `String`/`long` | not the cause |
| reading a flat field costs | tight loop, no Jackson | **0.00 B/element** |
| …once library calls are interleaved | 4 Jackson calls per element | **0.03 B/element** |
| deep nesting / large values | 48-byte `Customer` → `Address` → `Country` | **0.03 B/element** |
| the method is too big → split it | small per-object methods | unchanged |

What remains is the size and shape of that one serializer: ~420 bytes of bytecode, ~40 calls, a nested loop —
where C2 evidently stops scalar-replacing although it compiles the method at tier 4. In every smaller shape I
tested, reads of flattened fields are free.

I am leaving this unresolved rather than inventing a mechanism for it. The practical statement is: **scalarization
normally makes flat-field reads cost nothing, and this build has a threshold beyond which it quietly stops** —
which matters precisely because nothing in the source tells you which side of it you are on.

## 9. Your tools will lie to you

JOL 0.17 — the standard tool for this job — predates flat fields. It renders a flattened field as a 4-byte
reference, and when it walks an array of value objects it de-duplicates them by `==`, which for value objects
means *by value*. A `LocalDate[1_000_000]` filled with a repeating pattern comes back as "756 objects". There are
no objects at all; the array is flat. *(demo 02)*

Ask the JVM instead: `jdk.internal.value.ValueClass.isFlatArray`, `Unsafe.isFlatField`, `Unsafe.hasNullMarker`,
`Unsafe.nullMarkerOffset`, `Instrumentation.getObjectSize` for real sizes, and
`com.sun.management.ThreadMXBean.getCurrentThreadAllocatedBytes` for allocation. HotSpot's own
`-XX:+PrintFieldLayout` and `-XX:+PrintFlatArrayLayout` are the ground truth for layout, and they name the layout
kind: `NULLABLE_ATOMIC_FLAT`, `NULL_FREE_ATOMIC_FLAT`, `NULLABLE_NON_ATOMIC_FLAT`.

---

## What I would tell a team today

1. **Measure before migrating.** The same keyword gave 43 % on an invoice model and 0.13 % on an article model.
   The question is not "are value classes fast" but "how much of my data is small and primitive-like".
2. **Put value classes in records.** Record components are strict fields, which lifts the 64-bit limit entirely.
   A value class in a plain mutable field is the one combination that can make things worse.
3. **Watch the 7/8-byte boundary** for anything stored in arrays or ordinary fields. `(int, int)` is one byte too
   large, and there is no public syntax for null-restriction yet.
4. **Prefer loops to streams in hot paths** until generics are specialised — measured at 4× on this build.
5. **Audit for identity assumptions**: `WeakHashMap`/`WeakReference` as "have I seen this", `IdentityHashMap`
   visited sets, `synchronized` on ids, references used as capabilities, and anything writing fields through
   `sun.misc.Unsafe`. The loud failures (`IdentityException`) you will find in the first test run. The silent ones
   — a cache that over-admits 3×, a serializer that emits back-references it never used to — you will not.
6. **The upgrade alone gives you something.** `Integer`, `Short`, `LocalDate` and friends flatten into your
   existing entities with no code change: 7–15 % on the models I measured, and a `LocalDate` chain going from
   15.43 bytes per call to zero.

The thing I did not expect, after all of this, is how much of the outcome is decided by one byte of payload and by
whether the JIT happens to reach a method. Valhalla moves the cost of abstraction out of memory and into the
compiler — and the compiler is very good, right up to the point where it silently isn't.

---

*All experiments live in [github.com/LeszekLeszczynski/confitura2026](https://github.com/LeszekLeszczynski/confitura2026):
25 runnable demos, each printing the numbers quoted here. Run `./run.sh` for the list; `-p` enables preview.
Everything is measured, nothing is quoted from the JEP — and where the measurement contradicted the expectation,
the correction is in the text rather than quietly dropped.*
