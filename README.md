# Object layout demos — JDK 28 / Valhalla

A collection of small, independently runnable experiments about how objects are laid out on the heap
on JDK 28 (early access, build `28-ea+14`), with and without `--enable-preview` (JEP 401 value classes).
Each demo is a class with a `main`; run it, read the numbers, put them on a slide.

## Setup

- JDK 28-ea (sdkman `28.0.0+ea.14-open`), Maven 3.9, JOL 0.17, JMH 1.37, JUnit 5
- Compact object headers (`-XX:+UseCompactObjectHeaders`) are **on by default** in this JDK — an object header is 8 bytes, not 12
- Default JVM args for every demo and test (see `pom.xml`, `default.jvm.args`):
  `-XX:+EnableDynamicAgentLoading --sun-misc-unsafe-memory-access=allow --add-exports java.base/jdk.internal.value=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true -Djol.magicFieldOffset=true`
  — the exports let the helpers ask HotSpot about flat arrays and fields, the opens let Ehcache's graph walker
  reflect into `java.util`, and the rest keeps JOL working (agent attach, records) without warnings
- Output is kept quiet on purpose, so a demo on a projector shows only its own numbers: `.mvn/jvm.config` silences
  the warnings from Maven's *own* JVM (guava, sisu and jansi in `libexec` use `sun.misc.Unsafe`, native access and
  reflective final-field mutation), the flags above silence the demo JVM's, and `Demo`'s static initializer primes
  JOL quietly (it prints a one-time "cannot attach the Serviceability Agent" notice on macOS; the real addresses it
  would provide are never used). `Demo.vm()` still prints JOL's full VM details where a demo asks for them.

### Running

```
./run.sh                                         # lists demos; (-p) marks preview-only ones
./run.sh ObjectHeader                            # plain
./run.sh -p ObjectTable                          # compile + run with --enable-preview
./run.sh -p ArrayFlattening -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlatArrayLayout   # extra JVM flags
```

`-p` activates the Maven profile `preview`, which adds `--enable-preview` to the compiler, tests and runtime,
and adds two extra source roots that only compile with preview: `src/preview/java` and `src/preview-test/java`.
`run.sh` remembers the last mode in `target/.build-mode` and does a clean build when it changes (Maven's
incremental compiler does not notice changed compiler flags). Plain `mvn` users: `mvn clean test` / `mvn clean test -Ppreview`.

IntelliJ: run configurations for every demo are in `.run/`; enable the `preview` Maven profile in the Maven tool
window so the IDE indexes `src/preview/java`.

### Helpers (`src/main/java/.../confitura2026`)

| helper | what it does |
|---|---|
| `Demo.vm()` / `layout(...)` / `graph(...)` | thin wrappers over JOL (`VM.details`, `ClassLayout`, `GraphLayout`) |
| `Demo.shape(obj)` → `ObjectShape` | **Valhalla-aware** object-internals table: shows flattened fields inline, nested, with null markers. JOL 0.17 predates flat fields and renders them as 4-byte references |
| `Demo.sizeOf(obj)` | real shallow size via Instrumentation |
| `Demo.allocatedBytes()` | per-thread allocation counter (`com.sun.management.ThreadMXBean`) — diff two calls to measure a block, works under any GC |
| `Demo.isFlatArray(arr)` / `isFlatField(cls, name)` | asks HotSpot (`jdk.internal.value.ValueClass`, `jdk.internal.misc.Unsafe`) — the source of truth for "did it flatten?" |
| `Demo.newReferenceArray(...)` / `newNullRestrictedArray(...)` | arrays with a forced layout (non-flat, or flat without null markers) |
| `Demo.hasNullMarker(cls, name)` | does that field carry a null marker byte? false for references and null-restricted slots |
| `Javac.compile(name, source)` | compiles a one-class snippet in-process with preview on, and reports javac's errors — used to show what the *language* rejects (demos 12, 18) |

Tests: `DemoSmokeTest` / `PreviewDemoSmokeTest` run every demo's `main` so a JDK bump can't silently break one;
`FlatArraySpeedTest` checks that JMH's annotation processor actually generated the benchmark.

---

## The rule that explains most of what follows

On this build, **a value object in a place that can be reassigned — a field of an ordinary class, or an array
element — is flattened (stored inline, no header, no pointer) only if one element, payload plus a 1-byte null
marker, fits in a single atomic 64-bit access.** A concurrent write must never produce a torn read.

- nullable field / `new T[n]`: payload ≤ 7 bytes
- null-restricted field / array: payload ≤ 8 bytes
- flattened slots are padded to a power of two (3 B → 4, 5 B → 8)
- nesting is recursive, but every nested level rounds up on its own
- anything bigger is stored as a reference to a *buffered* heap copy, which carries its null marker with it
  (a buffered `Point(int,int)` is 24 B, an identity `record Point` is 16 B)

**A *strict* field escapes the limit.** A strict field is assigned before `super()` and never again, so no read
can race a write and the JVM uses a non-atomic layout: on this build javac emits strictness for **record
components** and **value-class fields**, and a `record Line(Point start, Point end)` inlines both points
(`NULLABLE_NON_ATOMIC_FLAT`, 9 B at 4-byte alignment), with payloads of 256 B still flattening
(`-XX:FlatteningBudget=1024` is the cap). Plain `final` does *not* count — javac marks it neither strict nor
trusted, since reflection can still rewrite it — and a strict field that is also `@NullRestricted` goes back
to the 8-byte atomic limit on this build (the null-free non-atomic layout needs the JDK-internal
`@LooselyConsistentValue` on the value class).

There is no public syntax for "null-restricted" in JDK 28; the demos use the JDK-internal
`@jdk.internal.vm.annotation.NullRestricted` (on a strict `final` field assigned before `super()`) and
`ValueClass.newNullRestrictedAtomicArray`, which HotSpot honours for application classes.

---

## Demos

The preview-only ones need `-p`; the rest run both ways, and the contrast between the two runs is usually the point.

| | demo | what it shows |
|---|---|---|
| 01 | `ObjectHeader` | what an empty object costs; compact vs classic headers |
| 02 | `ObjectTable` | a million `LocalDate`s through JOL — and why JOL miscounts value objects |
| 03 | `EpsilonAllocation` | the same, measured by the JVM instead: 20 → 8 bytes per element |
| 04 | `ArrayFlattening` *(-p)* | the 64-bit rule for array elements |
| 05 | `FlatArraySpeed` *(-p)* | JMH: flat wins 4.5–6× only when objects are scattered |
| 06 | `ValueObjectsInList` *(-p)* | collections cannot be flat — the trap |
| 07 | `CovariantIntegerArray` | `Comparable[] numbers = new Integer[N]`, and `==` on `Integer` flipping |
| 07b | `NullMarker` *(-p)* | where the extra byte comes from and what it costs |
| 08 | `FieldFlattening` *(-p)* | the same rule for fields; recursive flattening; strict fields |
| 09 | `IdentityOperations` | what now throws on `Integer`, `Optional`, `LocalDate` |
| 10 | `FrameworkInstantiation` *(-p)* | Kryo / Hibernate / Jackson instantiation strategies vs value classes |
| 11 | `ValueFieldsInEntity` | is an ordinary JPA entity still safe? |
| 12 | `DefaultValueBypass` *(-p)* | can a value object exist without its constructor running? |
| 13 | `IdentityMapBreakage` | `IdentityHashMap` quietly becomes a value map |
| 14 | `IdentityAssumptions` *(-p)* | when that is an outage or a security bypass |
| 15 | `CommonsLangToString` | commons-lang3 3.20.0 throws on any boxed field |
| 16 | `CacheSizing` | Ehcache 3.12.0 over-admits 3–4× its budget, silently |
| 17 | `OptionalValueClass` | `Optional` in a field stops allocating |
| 18 | `ValueClassRestrictions` *(-p)* | everything you cannot do, tried rather than quoted |
| 19 | `InvoiceModels` / `ValueInvoiceModels` | a real model three ways: −43 % bytes, −60 % objects |
| 20 | `ArticleModels` / `ValueArticleModels` | the counter-example: a string-heavy model saves 0.13 % |
| 21 | `JacksonInvoice` *(-p)* | the saving does not survive serialization |
| 22 | `PeriodVsLine` / `ValuePeriodVsLine` | one byte of payload decides flat vs not |
| 23 | `Scalarization` *(-p)* | the fields live in registers; the object is never built |
| 24 | `DateChain` / `ScalarizationInPractice` / `ScalarizationSpeed` | what that buys in practice, in bytes and in time — and why a `stream()` throws it away |
| 25 | `WarmUpCost` *(-p)* | the cost of reading a flattened field, and the moment it disappears |

### 01 `ObjectHeader` — what does an empty object cost?
`./run.sh ObjectHeader` · `./run.sh ObjectHeader -XX:-UseCompactObjectHeaders`

JOL `ClassLayout` of `Object`, an empty class and a class with one `int`. Header is 8 bytes (mark word only)
by default; with `-XX:-UseCompactObjectHeaders` the classic 8 B mark + 4 B class pointer appears and
`OneInt` goes from `[8 header][4 int][4 gap]` to `[12 header][4 int]` — same 16 bytes, different reason.

### 02 `ObjectTable` — a million `LocalDate`s, seen by JOL
`./run.sh ObjectTable` · `./run.sh -p ObjectTable`

`GraphLayout.parseInstance(dates).toFootprint()` for a `LocalDate[1_000_000]`.

| | plain | preview |
|---|---|---|
| array | 4,000,016 B (4 B refs) | **8,000,016 B (8 B flat elements)** |
| `LocalDate` objects | 1,000,000 × 16 B | 756 "objects" × 16 B (*) |
| total | 20 MB | 8 MB |

(*) `LocalDate` is a value class under preview and the array is flat, so there are no per-element objects at
all. JOL doesn't know that: it reads elements via reflection (getting buffered copies) and de-duplicates them by
`==`, which for value objects means *by value* — and the loop only produces lcm(27, 12, 28) = 756 distinct
dates. Lesson: **don't trust a pre-Valhalla tool to count value objects.** Also: the same source compiles to a
preview-dependent class file (`minor_version 65535`) just because it touches `LocalDate`.

### 03 `EpsilonAllocation` — ask the JVM, not JOL
`./run.sh EpsilonAllocation -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC` (and with `-p`)

Same million dates, measured with `Runtime` heap-used delta (exact under Epsilon, which never frees anything)
and the per-thread allocation counter (exact under any GC).

| | plain | preview |
|---|---|---|
| bytes allocated / element | 20 | **8** |

Preview allocates *only the array* — 8 B per element and not a single transient object, which Epsilon proves
and JOL could not (JOL sees only what is reachable at the end).

### 04 `ArrayFlattening` — the 64-bit boundary  *(preview only)*
`./run.sh -p ArrayFlattening` · add `-XX:+UnlockDiagnosticVMOptions -XX:+PrintFlatArrayLayout` to see HotSpot say it

`value record Small(int, short, byte)` (7 B) vs `value record Big(long × 8)` (64 B), a million of each.

| | `isFlatArray` | bytes / element |
|---|---|---|
| `Small[]` | true | 9 (8 B slot + interpreter warm-up noise) |
| `Big[]` | false | 84 (4 B ref + 72 B buffered object + noise) |

Probing all sizes (4…128 B) and all four array kinds gave the rule above: 7 B is the last nullable size that
flattens, 8 B flattens only null-restricted, 12 B+ never (not even non-atomic null-restricted on this build).

### 05 `FlatArraySpeed` — JMH: does flat mean fast?  *(preview only)*
`./run.sh -p FlatArraySpeed` (full matrix ≈ 2.5 min)

Sum one `int` field over n elements: flat `Small[]` vs the same value class behind references
(`ValueClass.newReferenceArray`) vs an identity `record`. `scattered=true` fills the reference arrays in
shuffled order so objects aren't allocated adjacently — what a heap looks like after a few GC cycles.

| n = 16M | sequential | scattered |
|---|---|---|
| flat | 10.9 ms | **10.4 ms** |
| refs to value objects | 11.4 ms | 47.1 ms |
| identity records | 11.3 ms | **63.2 ms** |

At n = 1M everything fits in cache and nothing differs much. Flat wins 4.5–6× only when the objects are
scattered; when they happen to be sequential the prefetcher hides the pointer chasing completely. The win is
*locality that can't be destroyed*, not a faster load — C2 doesn't vectorise flat-array loads on this build and
the loop is bound by the `sum +=` dependency chain (~0.65 ns/element).

Build note: since JDK 23 javac no longer picks annotation processors up from the classpath; JMH's must be listed
in `annotationProcessorPaths` or it silently generates nothing (the test catches this).

### 06 `ValueObjectsInList` — the trap  *(preview only)*
`./run.sh -p ValueObjectsInList`

Every `java.util` collection stores elements in an `Object[]`, which cannot be flat.

| container | bytes / element |
|---|---|
| `Small[]` | **8** |
| `new ArrayList<>(N)` + `add` | 20 |
| `new ArrayList<>()` + `add` (growing) | 30 |
| `Arrays.asList(array)` | ~0 — wraps the original flat array |
| `List.of(array)`, `Arrays.stream(array).toList()` | 20 |
| `new ArrayList<>(Arrays.asList(array))` | 24 |

Put a value object in a `List` and it is *buffered* — materialised as a heap object with a header — and you're
back to the pre-Valhalla layout. `Arrays.asList` is the only one that keeps the array, and only until something
generic copies it. Value classes save memory in **fields and arrays, not in collections** (yet).

### 07 `CovariantIntegerArray` — `Comparable[] numbers = new Integer[N]`
`./run.sh CovariantIntegerArray` · `./run.sh -p CovariantIntegerArray`

Same class, run twice.

| | plain | preview |
|---|---|---|
| `Integer.class.isValue()` | false | **true** |
| `new Integer[N]` flat, viewed as `Comparable[]` | false | **true** |
| bytes / element for `numbers[i] = i` | 19 | 10 |
| `numbers[0] = "boom"` | ArrayStoreException | ArrayStoreException |
| `numbers[1] = null` | ok | ok |
| `Long[]` flat | false | false (8 B + marker > 64 bits) |
| `Short[]` flat | false | true |
| `Integer a = 1000, b = 1000; a == b` | **false** | **true** |

Covariance is fully preserved — the array is flat because of what it *is*, not how it's declared; every
`aaload`/`aastore` through a non-final static type checks the runtime layout. `Integer` migrated to a value
class, so autoboxing into arrays no longer allocates and the famous `-128..127` cache boundary is gone —
`==` on `Integer` now compares values.

### 07b `NullMarker` — where the extra byte comes from, and what it costs  *(preview only)*
`./run.sh -p NullMarker` · add `-XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout` for HotSpot's own view

A reference can be null for free: the pointer has a spare value. A flat slot has no pointer — the bytes *are* the
value, and `new Money(0, 0)` is 8 bytes of zeros and a perfectly good value, so "all zeros" cannot mean absent.
Hence one byte outside the payload: **is there anything here?** A null-restricted slot promises null never
happens, so it needs no byte.

**On the heap, a buffered value carries that byte too** — its layout is deliberately identical to a nullable flat
slot, which makes moving a value between heap and slot a plain memory copy (HotSpot prints `BUFFERED layout: 9/8`
for an 8-byte payload, and every nullable value class gets a hidden static `.null_reset` prototype used when
storing null). That is the tax that makes a value object bigger than the identity record it replaced:

| payload | buffered value | identity record | extra |
|---|---|---|---|
| 7 bytes | 16 B | 16 B | 0 |
| **8 bytes** | 24 B | 16 B | **8** |
| 12 bytes | 24 B | 24 B | 0 |
| **16 bytes** | 32 B | 24 B | **8** |
| **64 bytes** | 80 B | 72 B | **8** |

Eight extra bytes exactly when the payload fills its last 8-byte word, so the marker has to start a new one —
which is the common case for `int`/`long` pairs.

The same `Money(int cents, int currency)` in the three kinds of slot:

| slot | flat | marker | holder | total |
|---|---|---|---|---|
| `Money money` — plain reassignable class field | no | — | 16 B | **40 B**, two objects |
| `record NullableHolder(Money money)` — strict, nullable | yes | yes | **24 B**, one object | 24 B |
| `@NullRestricted final Money` — strict, null-free | yes | **no** | **16 B**, one object | 16 B |

**A null is still just a null reference — never an object with the marker set to "absent".** `new
NullableHolder(null).money() == null` is true, the holder is the same 24 B either way (the slot exists
regardless of what is in it), and storing `null` or a value into the flat slot both allocate only the holder:
24 B/op, no object for the value and none for the null.

At scale the marker is the whole story for small values: `new Pixel[1_000_000]` (4 bytes of payload) is
8,000,016 B — 4 payload + 1 marker rounded to 8 — while the null-restricted array is 4,000,016 B, 4 bytes per
element, exactly an `int[]`.

### 08 `FieldFlattening` — the same rule, for fields  *(preview only)*
`./run.sh -p FieldFlattening` · add `-XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout` for HotSpot's own view

Uses `Demo.shape(...)`, since JOL renders flat fields as references.

| class | fields flat? | size |
|---|---|---|
| `value record Point(int, int)` (buffered form) | — | 24 B: 8 header + 8 payload + null marker + alignment |
| `IdentityLine { record Point ×2 }` | no | 16 B + 2 × 16 B objects |
| `Line { value Point ×2 }` (class, nullable) | **no** | 16 B + 2 × **24 B** — *bigger* than identity |
| `record RecordLine(Point, Point)` (strict components) | **yes**, non-atomic | **32 B**, one object (9 B per point incl. null marker) |
| `NullFreeLine { @NullRestricted final Point ×2 }` | **yes** | **24 B**, one object |
| `Sprite { Pixel color }` with `Pixel(@NullRestricted Rgb rgb, byte alpha)` | yes, recursively | 16 B: `Rgb` bytes at 8–10, `alpha` at 12, null marker at 13 |
| `Sprite5 { Pixel ×5 }` | yes, each independently | 48 B: five 8 B slots |

Arrays of 1M:

| | nullable | null-restricted |
|---|---|---|
| `Pixel[]` (nested `Rgb` + `alpha`) | 8 B/elem | 8 B/elem |
| `PackedPixel[]` (`byte r, g, b, a`) | 8 B/elem | **4 B/elem** |

Same 4 bytes of data, but the nested null-free `Rgb` gets an atomic 4 B slot, pushing `alpha` to offset 4 and
the payload to 5 B → 8. **Flattening is recursive, but each level rounds up to a power of two.** Naively turning
a `record` into a `value record` can make the enclosing object *larger* if the field doesn't qualify — and
whether it qualifies depends on the *holder*: the same `Point` is a 24 B heap object behind a class field and
9 inline bytes inside a record component.

### 09 `IdentityOperations` — things that now throw
`./run.sh IdentityOperations` · `./run.sh -p IdentityOperations`

Two separately constructed, equal `Integer` / `Optional` / `LocalDate` instances.

| operation | plain | preview |
|---|---|---|
| `synchronized (a) {}` | ok | **`IdentityException`** |
| `new WeakReference<>(a)` | ok | **`IdentityException`** |
| `Cleaner.register(a, …)` | ok | **`IdentityException`** |
| `a == b` | false | true |
| `identityHashCode(a) == identityHashCode(b)` | false | true |
| `IdentityHashMap {a → 1}.get(b)` | null | 1 |

None of these classes are ours. The first three are the migration hazard: code that compiled and worked for
decades throws at runtime the day the JDK flips the class (javac has warned about `synchronized` on value-based
classes since JDK 16). The last three flip silently — anything using `IdentityHashMap` to track "have I seen this
object" (serialisers, cycle detectors, deep-copy utilities) now conflates equal values.

### 10 `FrameworkInstantiation` — which framework tricks still work?  *(preview only)*
`./run.sh -p FrameworkInstantiation`

Three ways frameworks build objects behind your back, against four target types with the same two components:

| strategy | mutable POJO | record | value record | value class (no-arg ctor) |
|---|---|---|---|---|
| **A** `Unsafe.allocateInstance` + `Unsafe.put*` (Kryo) | OK | `UnsupportedOperationException`: can't get field offset on a **record** class | same | `UnsupportedOperationException`: …on a **value** class |
| **B** no-arg ctor + `Field.set` (Hibernate `@Embeddable`, field access) | OK | `NoSuchMethodException` `<init>()` | same | **`IllegalAccessException`: Can not set final field** |
| **C** `Constructor.newInstance(values)` (Hibernate 6 `EmbeddableInstantiator`, Jackson creators) | OK | OK | OK | OK |

Only the constructor path survives, so anything already record-aware works with value classes for free. Row B is
the interesting cell: the no-arg constructor is *found and runs*, and then `Field.set` refuses — a value class's
fields are final and `setAccessible` cannot open them, because there is no identity to mutate. Row A never starts:
`Unsafe` refuses to hand out a field offset, which is what stops a torn or half-written value object existing.

### 11 `ValueFieldsInEntity` — is an ordinary JPA entity still safe?
`./run.sh ValueFieldsInEntity` · `./run.sh -p ValueFieldsInEntity`

A mutable `Order` entity (no-arg constructor, field access, `Serializable`) whose fields are `Long`, `Integer`,
`Short`, `Double`, `LocalDate`, `LocalDateTime`, `String` — nothing of ours changed, only the JDK under it.

| | plain | preview |
|---|---|---|
| layout | 7 references | `Integer`, `Short`, `LocalDate` **flat inside the entity**; `Long`, `Double`, `LocalDateTime` still references (payload + marker > 64 bits) |
| no-arg ctor + `Field.set` (Hibernate field access), incl. `null` | ok | **ok** |
| `VarHandle` writes (bytecode enhancement) | ok | **ok** |
| `Unsafe.putObject` by field offset (Kryo, Objenesis-style) | ok | **silent corruption**: `amount` → `null`, `quantity` → `-23880`, `createdOn` → `null`, no exception |
| `ObjectInputStream` round-trip | ok | **ok** |
| dirty check `snapshot.amount == entity.amount` (equal values) | false | true (`equals` true in both — dirty checking is unaffected) |
| `synchronized (order.id)` | ok | `IdentityException` — and javac rejects it at compile time when the static type is `Long` |
| `WeakHashMap<Long, Order>`, `WeakReference<>(order.createdOn)` | ok | `IdentityException` |

**Verdict:** nothing to do for Hibernate's own hydration, Jackson, or Java serialization — reflection and
`VarHandle`s understand flat fields, including nulls, and the entity gets *smaller* (48 B, three fewer objects).
Audit two things: libraries that write fields through `sun.misc.Unsafe` (the entity is still an identity class, so
the offset is handed out, and a 4-byte reference then lands in a slot the JVM reads as a flat `int` + marker), and
id-based locking or weak caches, which fail loudly.

### 12 `DefaultValueBypass` — can a value object exist without its constructor running?  *(preview only)*
`./run.sh -p DefaultValueBypass`

`value record Money(long amount, int currency)` with a validating compact constructor (rejects negative
amounts and non-positive currencies) and a static invocation counter; plus `SmallMoney(int, int)` — same
contract, 8 bytes so it can actually be flattened when null-restricted (`Money` is 12 bytes and never flattens,
so its "null-restricted" fields and arrays are reference-based). Every route by which a `Money` might come into
existence is tried, and each line reports whether the constructor ran.

**Answer: yes — on this build a `Money(0, 0)` that no constructor ever produced can be obtained, but only
through `Unsafe.allocateInstance`. Every language-level and `ValueClass`-level route refuses.**

| route | result | ctor ran? |
|---|---|---|
| `new Money(-1, 985)` | `IllegalArgumentException: negative: -1` | yes |
| **1.** `ValueClass.newNullRestrictedAtomicArray(Money.class, 3, null)` | `NullPointerException: Initial value is null` — the signature is `(Class, int, Object initialValue)` and the value is mandatory | no |
| `newNullRestrictedAtomicArray(Money.class, 3, valid)[0]` | a copy of `valid` — a value the constructor *did* produce earlier | no (copy) |
| `newNullRestrictedNonAtomicArray(..., null)` | same `NullPointerException` — same signature | no |
| `newNullableAtomicArray(SmallMoney.class, 3)[0]`, `(new SmallMoney[3])[0]` | `null` | no |
| **2.** `@NullRestricted final M m;` never assigned | **javac rejects**: "variable m not initialized in the default constructor" | — |
| read `m` before assigning it in the prologue | **javac rejects**: "variable m might not have been initialized" | — |
| assign `m` *after* `super()` | compiles, **verifier rejects at load**: "All strict final fields must be initialized before super()" | — |
| non-final `@NullRestricted M m;` | compiles, same `VerifyError` — HotSpot treats null-restricted fields as strict regardless of `final` | — |
| `ReflectionFactory.newConstructorForSerialization(SmallAccount)` | `UnsupportedOperationException: … declares a strictly-initialized instance field` | no |
| `ObjectInputStream` round-trip of a holder | `InvalidClassException: cannot serialize due to final value class or strictly-initialized instance fields` | no |
| `Unsafe.allocateInstance(SmallAccount.class).balance` (flat `@NullRestricted` field, `sun.misc` or internal) | **`SmallMoney[amount=0, currency=0]`** | **no** |
| `Unsafe.allocateInstance(Account.class).balance` (non-flat `@NullRestricted` field) | `null` — a null in a null-restricted field, but not a fabricated `Money` | no |
| **3.** `jdk.internal.misc.Unsafe.newSpecialArray(Money.class, 3, layoutKind)` — the only array factory without an initial-value parameter | layout 0: `IllegalArgumentException: Invalid layout kind`; layout 1: **JVM crash** `Internal Error (arrayKlass.cpp:234) ShouldNotReachHere` (run in a forked JVM, exit 134) | no |
| **4.** `jdk.internal.misc.Unsafe.allocateInstance(Money.class)` | **`Money[amount=0, currency=0]`** | **no** |
| `sun.misc.Unsafe.allocateInstance(Money.class)` — no `--add-exports`, plain `jdk.unsupported` | **`Money[amount=0, currency=0]`** | **no** |

Tally at the end of a run: constructors ran 3 times and produced 2 distinct values; **4 values were observed
that no constructor produced** — all `(0, 0)`, all via `allocateInstance`.

What prevents it everywhere else, in order of the layer that says no:
- **API shape** — `ValueClass.newNullRestricted*Array` demands an explicit non-null initial value, which must
  itself have come from a constructor. There is no public zero-filling factory.
- **javac** — a null-restricted (strict) field must be definitely assigned before `super()`, and cannot be read before it.
- **the verifier** — even hand-crafted bytecode that assigns a strict field after `super()` is rejected at class load.
- **serialization** — both `ObjectInputStream` and `ReflectionFactory` refuse classes with strict fields outright.
- **`Unsafe.allocateInstance`** is the one door left open. It has always meant "give me zeroed memory of this
  class, no constructor" — and for a value class, zeroed memory *is* a valid instance: `Money(0, 0)`, or a holder
  whose flat null-restricted field reads back as `SmallMoney(0, 0)`. Objenesis-style libraries, mocking frameworks
  and some serializers use exactly this call, via `sun.misc.Unsafe`, with no flags required.

Practical reading: a value class's constructor invariants hold against everything the language and the
`java.base` APIs let you do, but not against libraries that bypass constructors on purpose — the same
libraries that already fabricate identity objects without running their constructors. What is new is that the
fabricated value is indistinguishable from a real one: no identity, `==`-equal to any other `Money(0, 0)`, and
it can sit inside a flat, null-restricted field where `null` would previously have made the omission visible.

---

### 13 `IdentityMapBreakage` — `IdentityHashMap` stops being an identity map
`./run.sh IdentityMapBreakage` · `./run.sh -p IdentityMapBreakage`

Two textbook uses of `IdentityHashMap`, fed a graph whose leaves are `Integer` and `LocalDate`.

| | plain | preview |
|---|---|---|
| serializer with back-references: `Invoice{net, gross, issued, due, parent=this}` | `net=1000, gross=1000, issued=…, due=…, parent=@ref1` | `net=1000, **gross=@ref2**, issued=…, **due=@ref3**, parent=@ref1` |
| footprint estimator: distinct objects in a `List` of 1000 equal `Integer`s | **1001** | **2** |

The genuine cycle (`parent`) is still caught — `Invoice` is an identity class. But equal values now collide:
`IdentityHashMap` silently degrades into a `HashMap` for value-class keys, so "have I seen *this object*" becomes
"have I seen *this value*". Nothing throws.

### 14 `IdentityAssumptions` — when that is not cosmetic  *(preview only)*
`./run.sh -p IdentityAssumptions`

Two places where the identity assumption is load-bearing, each with an identity twin alongside:

| | identity version | value version |
|---|---|---|
| **tree-only serializer** (formats that cannot express sharing — plain JSON, Hibernate's own "found shared references" check) | `{"created": "…", "updated": "…"}` | **`IllegalStateException`: shared reference: 2026-09-19 at $.created and $.updated** |
| **capability registry** — a token is valid because the vault handed *that object* out, kept in an identity set | forged token → `SecurityException` | forged token → **`withdrew 1000000`** |

The first is an outage: an order created and updated on the same day now throws on the way out, in code nobody
changed. The second is a security bypass: `new ValueToken(1)` built by an attacker is `==` to the issued one, so
the identity set accepts it. Unforgeability of a reference was the whole mechanism, and `value` deleted it with no
warning and no failing test.

### 15 `CommonsLangToString` — a real library, current release
`./run.sh CommonsLangToString` · `./run.sh -p CommonsLangToString`

Apache Commons Lang **3.20.0** (the current release, out since 2025-11), unmodified.

| | plain | preview |
|---|---|---|
| `ReflectionToStringBuilder.toString(order)` — `Integer`/`LocalDate` fields | ok | **`IdentityException`** @ `ToStringStyle.register:608` |
| …on a class with only `int`, `long`, `String` fields | ok | **`IdentityException`** (reflection boxes primitives) |
| hand-written `toString()`: `append("id", Long)` → `append(String, Object)` | ok | **`IdentityException`** |
| `append("created", LocalDate)` | ok | **`IdentityException`** |
| `append("name", String)` / `append("total", long)` (primitive overload) | ok | ok |
| `EqualsBuilder` / `HashCodeBuilder` reflective forms | ok | ok (different registry, built on `identityHashCode`) |
| `new WeakHashMap<>().put(1000L, null)` | ok | `IdentityException` |

`ToStringStyle`'s cycle-detection registry is a `ThreadLocal<WeakHashMap<Object, Object>>`, and `register()` puts
every visited value into it — so `WeakHashMap.put` → `WeakReference` → `Objects.requireIdentity` throws on any
boxed number or date. **It is not about reflection**: the IDE-generated hand-written `toString()` fails
identically. The vulnerable surface is every `ToStringBuilder` usage that appends a boxed field, which is most of
them. `EqualsBuilder`/`HashCodeBuilder` are unaffected.

### 16 `CacheSizing` — the silent one
`./run.sh CacheSizing` · `./run.sh -p CacheSizing`

Ehcache **3.12.0** (current, 2026-04) bundles the `sizeof` engine behind its byte-sized heap tiers; its
`ObjectGraphWalker` keeps visited objects in an `IdentityHashMap`. Nothing throws, ever.

A `List<Integer>` of 1000 separately boxed `Integer.valueOf(1000)`, by item:

| item | count × size | bytes |
|---|---|---|
| `ArrayList` shell | 1 × 24 | 24 |
| `Object[1000]` backing array (12 B header + 4/ref) | 1 × 4,016 | 4,016 |
| `Integer` (8 B header + `int` + padding) | 1,000 × 16 | 16,000 |
| **really on the heap** | | **20,040** |
| reported by Ehcache sizeof, plain | | 20,040 ✓ |
| reported by Ehcache sizeof, preview | | **4,056** — it counted *one* `Integer` |

With a 200 KB budget and entries of 100 equal values (true cost 2,040 B, charged 456 B under preview):

| | entries admitted | charged | really held |
|---|---|---|---|
| plain | 100 | 204,000 | 204,000 (100 %) |
| preview | **449** | 204,744 | **915,960 (447 %)** |

And the real product, `heap(200, MemoryUnit.KB)` with 2000 puts: **91 entries retained plain, 312 under preview** —
185,640 B (91 %) versus 636,480 B (311 %), the difference being ~210 B of Ehcache overhead per mapping. The error
is exactly `(n − 1) × 16` bytes per entry, n = number of equal values in it. A cache that believes it is at its
budget while holding 3–4× that is the kind of bug that surfaces as an OOM weeks later, with the cache's own
metrics insisting everything is fine. (Only the agent strategy is silent: if the agent cannot attach, `UnsafeSizeOf`
fails loudly instead, so which failure a deployment gets depends on `jdk.attach.allowAttachSelf`.)

### 17 `OptionalValueClass` — `Optional` becomes a value class
`./run.sh OptionalValueClass` · `./run.sh -p OptionalValueClass`

| | plain | preview |
|---|---|---|
| `Customer { Optional<String> nickname; Optional<String> referrer; }` | 16 B + 2 × 16 B = 48 B, 3 objects | **24 B, 1 object** — each `Optional` inlined as its 4-byte reference + marker |
| `new Optional[1_000_000]` | 4,000,016 B of references | **8,000,016 B, flat** |
| `find(i).orElse("none")` — consumed at once | 0 B/call | 0 B/call |
| `find(i).map(String::length)` — chained | 0 B/call | 0 B/call |
| **`customer.nickname = find(i)` — stored in a field** | 8 B/call | **0 B/call** |
| `list.add(find(i))` — stored in a `List` | 8 B/call | 8 B/call |
| `Optional.of("ada") == Optional.of("ada")` | false | true |

The local cases were already free before Valhalla — C2 scalar-replaces an `Optional` that never escapes, which is
why "`Optional` is expensive" was mostly a myth. What changes is the case the old advice warned about: storing one
in a field. The `List` row is unchanged, because `Object[]` cannot be flat (demo 06).

### 18 `ValueClassRestrictions` — what you cannot do  *(preview only)*
`./run.sh -p ValueClassRestrictions`

Everything tried, not quoted from the JEP. **javac**, on declarations:

| | verdict |
|---|---|
| mutable field | `cannot assign a value to final variable` — fields are implicitly final |
| `synchronized` method | `modifier synchronized not allowed here` |
| `value class V extends IdentityClass` | `The identity type … cannot be a supertype of the value type …` |
| `class W extends V` (V a value class) | `cannot inherit from final V` |
| instance initializer `{ x = 1; }` | `strict field x is not initialized before the supertype constructor` |
| `this` before fields are set | `reference to this may only appear after an explicit constructor invocation` |
| `synchronized (v)` on a value-typed variable | `unexpected type` — rejected statically |
| *allowed:* read a field after assigning it, `abstract value class` base | compiles |

**The JVM**, at runtime: `synchronized`, `WeakReference`/`SoftReference`/`PhantomReference`, `WeakHashMap.put`,
`Cleaner.register`, `Objects.requireIdentity` all throw `IdentityException`; `wait()` throws
`IllegalMonitorStateException` (you can never own the monitor); `identityHashCode` and `IdentityHashMap` work but
by value. Reflection: `setAccessible`, `Field.get` and `findGetter` work; `Field.set`, `findSetter`,
`unreflectSetter`, `VarHandle.set`/`compareAndSet` and `Unsafe.objectFieldOffset` all refuse; `Constructor.newInstance`
works; `Unsafe.allocateInstance` works (demo 12's open door). A `Serializable` value record round-trips fine.

### 19 `InvoiceModels` / `ValueInvoiceModels` — a real model, three ways
`./run.sh InvoiceModels` · `./run.sh -p InvoiceModels` · `./run.sh -p ValueInvoiceModels`

An invoice with a customer (id, name, VAT id, since, loyalty points, address with country code, phone) and 10 line
items (sku, quantity, unit price, tax rate, delivery date), built 10,000 times. **A**: mutable classes with wrapper
types, as a JPA entity looks. **B**: immutable records. **C**: records plus value records for the small things.

| bytes per invoice | plain (nothing flattened) | preview |
|---|---|---|
| **A** classic classes | **1,153** | 1,059 (−8 %) |
| **B** records | 1,108 | 948 (−18 %) |
| **C** records + value records | — | **660 (−43 % vs. plain A)** |

`VInvoice` is **96 bytes, one object**: invoice → customer → address → country code, four levels of value records
all inline, because record components are strict fields and the 64-bit limit does not apply to them. Only the
`String`s and the `List` remain references. Plain A → plain B is worth almost nothing (records are about
immutability, not bytes); the JDK upgrade alone gives 7–15 % as `Integer`/`Long`/`LocalDate` flatten into whatever
holds them; the last 28 % is one keyword on your own small types. The floor is the `List` and its ten buffered
`VLineItem`s — and an array would not help: `VLineItem` is ~40 bytes of payload, far over the array limit, so
`VLineItem[]` is a reference array (verified).

### 20 `ArticleModels` / `ValueArticleModels` — the counter-example
`./run.sh ArticleModels` · `./run.sh -p ValueArticleModels`

The same treatment for a model that is almost entirely `String`s: an article with an author, 4 tags and 5 sections,
every string freshly allocated. Measured on pre-made strings, so only the model's own objects count.

| model | structure / article | total / article | strings' share |
|---|---|---|---|
| **A** classic classes | 324 B | 2,980 B | 89 % |
| **B** records | 324 B | 2,980 B | 89 % |
| **C** records + value records | **320 B** | 2,976 B | 89 % |

**Four bytes, 0.13 %.** `VAuthor` does flatten into the article, but four references inline instead of four
references behind a pointer saves only the author's header, and alignment eats half of it; the sections sit in a
`List`, so they stay buffered objects; and the 2,656 B of `String` + `byte[]` are untouchable. Valhalla pays in
proportion to how much of your data is small and primitive-like — text-heavy models see nothing. Same tools, same
keyword, opposite outcome, which is the argument for measuring before migrating.

### 21 `JacksonInvoice` — does the saving survive a REST round-trip?  *(preview only)*
`./run.sh -p JacksonInvoice`

Jackson 2.22.2 on `ClassicInvoice` vs `VInvoice`. **It works**: byte-identical JSON (1,582 chars, same tree),
deserialization through the canonical constructors, `equals(original)` true. (`customer == original.customer` is
false — a value object's `==` compares fields with `==`, and the deserialized `String`s are different instances.)

| bytes allocated per invoice | build model | Jackson databind | hand-written streaming |
|---|---|---|---|
| `ClassicInvoice` | 1,055 | 3,672 | 1,696 |
| `VInvoice` | **656** (−400) | **4,069** (+400) | **2,839** (+1,140) |

Build + serialize is a wash to within 2 bytes, and the hand-written streaming serializer — no reflection,
all accessors C2-inlined (checked with `-XX:+PrintInlining`) — is *worse*. So for a DTO whose whole purpose is
serialization, today the saving is zero or negative: **value classes shrink objects you keep, not objects you
pass through.**

**Why** is not settled, and the obvious explanations are all wrong — each was tested and ruled out:

| hypothesis | test | result |
|---|---|---|
| erasure: values pass through `Object` | Jackson's generator takes `String`/`long`, not `Object` | not the cause |
| reading a flat field costs | tight loop reading flat fields, no Jackson | **0.00 B/element** |
| …costs once library calls are interleaved | 4 Jackson calls per element + flat-field reads | **0.03 B/element** |
| deep nesting / large values | 48-byte `Customer` → `Address` → `Country`, 6 Jackson calls | **0.03 B/element** |
| the method is too big → split it up | same output through small per-object methods | unchanged |

What is left is the size and shape of that one serializer method: ~420 bytes of bytecode, ~40 calls and a nested
loop, where C2 evidently stops scalar-replacing although it compiles the method at tier 4. In every smaller shape
tested, reads of flattened fields are free. So the honest statement for a talk is: **scalarization normally makes
flat-field reads cost nothing, and this build has a threshold beyond which it quietly stops** — which is worth
knowing precisely because nothing in the code tells you which side of it you are on. (Contrast demo 24's stream
row, where the allocation is structural: erasure to `Object`, unfixable by any JIT tier.)

### 22 `PeriodVsLine` / `ValuePeriodVsLine` — one byte decides it
`./run.sh PeriodVsLine` · `./run.sh -p PeriodVsLine` · `./run.sh -p ValuePeriodVsLine`

Two holders with the identical shape — two small immutable fields in an ordinary mutable class:

```java
class Period { LocalDate start; LocalDate end; }        value class Point { int x; int y; }
                                                        class Line { Point start; Point end; }
```

| holder | fields flat? | holder | + fields = total | objects |
|---|---|---|---|---|
| `Period` (2 × `LocalDate`), plain | false | 16 B | 48 B | 3 |
| `Period`, **preview** | **true** | **24 B** | **24 B** | **1** |
| `Line` (2 × value `Point`), preview | false | 16 B | **64 B** | 3 |
| `IdentityLine` (2 × `class Point`), either mode | false | 16 B | 48 B | 3 |
| `record RecordLine(Point, Point)`, preview | **true** | **32 B** | **32 B** | **1** |

Pieces: `LocalDate` 16 B, buffered value `Point` **24 B**, identity `Point` 16 B.

`LocalDate`'s payload is 7 bytes (`int year`, `byte month`, `byte day`), so payload + marker = 8 and it fits an
atomic slot: the class the JDK converted for you halves, with no code changes. `Point`'s payload is 8, so it does
not fit — and because each `Point` on the heap now carries a marker byte (24 B instead of 16), **`Line` is bigger
than the identity version it replaced**. `RecordLine` is the escape hatch: the same two points as record
components are strict fields, the limit does not apply, and you get one 32-byte object.

### 23 `Scalarization` — the fields, not the object  *(preview only)*
`./run.sh -p Scalarization`

Flattening is about data on the heap; scalarization is about code. The same loop —
`s = s.plus(new Point(xs[i], ys[i]))`, 10,000 times — with the second column measured in a forked JVM where
`plus` may not be inlined, so escape analysis has nothing to work with.

| | inlining allowed | dontinline |
|---|---|---|
| identity record `Point` | 16.00 B/op | 32.00 B/op |
| **value record `Point` (8 B)** | **0.00** | **0.00** |
| **value record `Big` (32 B)** | **0.00** | **0.00** |
| value `Point` through an `Object` parameter | 0.00 | **48.00 B/op** |

Three things at once. The identity record allocates even *with* inlining — escape analysis removed only one of the
two objects per iteration — so this is not a straw man. The value record allocates nothing even when the call is
not inlined, because the compiled method passes and returns the fields instead of a pointer. `Big` proves there is
no size limit: the stack has no races between threads, so tearing is not a concern and the 32-byte payload that
could never flatten in a field scalarizes completely. And the last row is the condition: pass the same value
through an `Object` parameter and it costs **more** than the identity version (48 B — two buffered `Point`s per
iteration, each carrying its null marker), because erasure leaves the JIT nothing to scalarize.

### 24 `DateChain` / `ScalarizationInPractice` — what it buys, and what throws it away
`./run.sh DateChain` · `./run.sh -p DateChain` · `./run.sh -p ScalarizationInPractice`

`DateChain` is the benefit that needs no code change: `date.plusDays(i).withDayOfMonth(1).plusMonths(3)` —
two intermediates and a result — costs **15.43 bytes per chain plain and 0.00 with preview**. (Single-step chains
are 0.00 either way: escape analysis already handled those, which is the honest caveat.)

`ScalarizationInPractice` measures the patterns worth putting in a domain model. The second column is a forked JVM
limited to C1 (`-XX:TieredStopAtLevel=1`) — what every row looks like before C2 reaches the method:

| bytes per element | warmed up (C2) | C1 only |
|---|---|---|
| return `MinMax` (value record) | 0.00 | 24.00 |
| return `MinMax` (identity record) | 0.00 | 16.00 |
| return two ints packed in a `long` | 0.00 | 0.00 |
| pricing loop, value `Money` | **0.00** | 72.00 |
| pricing loop, identity `Money` | **16.00** | 32.00 |
| pricing loop, raw `long` | 0.00 | 24.00 |
| `for` loop, `total.plus(m)` | **0.00** | 24.00 |
| **`stream().reduce(ZERO, Money::plus)`** | **24.01** | 24.01 |
| `stream().map(...).reduce(...)` | **48.02** | 72.02 |
| `stream().mapToLong(...).sum()` | 0.02 | 24.02 |

The practical lesson is the stream rows. A `for` loop over 10,000 amounts allocates nothing; the same arithmetic
through `reduce` allocates 24 B per element — a quarter of a megabyte per pass — and it is the **only** kind of
allocation here that C2 cannot remove, because `BinaryOperator<T>` erases to `Object` (demo 23's last row). Chain a
`map` in front and it doubles. Escaping to `mapToLong` gets back to zero, because the value never crosses a generic
boundary. So: **in hot code with value classes, prefer the loop** — until generics over value types are specialised.

The pricing rows say the other half: warmed up, the domain type is free while its identity twin still allocates
16 B per element. Everything else in the C1 column is demo 25's transient warm-up cost, not a property of the code.

**And it is faster, not just leaner.** `ScalarizationSpeed` (JMH, `-prof gc` built in, ~1 min) runs one loop —
the pricing calculation — three ways over 10,000 elements, and reports time and allocation side by side. The three
`sum` rows below were measured with the same harness and are kept here for reference; the checked-in benchmark
stays on the pricing comparison so it is quick enough to run live:

| benchmark | time | allocation |
|---|---|---|
| `pricingValue` — value `Money` | **12.43 µs/op** | **0.08 B/op** |
| `pricingIdentity` — identity twin | 22.45 µs/op | 160,000 B/op |
| `pricingRawLong` — no domain type at all | 11.58 µs/op | 0.07 B/op |
| `sumLoopValue` — `for` + `total.plus(m)` | **5.17 µs/op** | **0.03 B/op** |
| `sumLoopIdentity` — identity twin | 19.25 µs/op | 160,000 B/op |
| `sumStreamValue` — `stream().reduce(Money::plus)` | 22.43 µs/op | 240,120 B/op |

The pricing loop is **1.8× faster** than its identity twin and lands within 7 % of a raw `long` — the domain type
costs essentially nothing. The plain summation is **3.7× faster**. And the stream row is the same value type doing
the same arithmetic **4.3× slower than the loop**, purely because `BinaryOperator<T>` erases to `Object`: 240 KB of
buffered `Money` per operation, at 10 GB/s of allocation rate. So the earlier "prefer the loop" is not only about
GC pressure — it is a 4× difference in wall-clock time on this build.

### 25 `WarmUpCost` — reading a flattened field, before and after C2  *(preview only)*
`./run.sh -p WarmUpCost`

`record Risk(Money base, int loading)` holds its `Money` inline, so no `Money` object exists on the heap. Asking
for one — `risk.base().cents()` — therefore has to build it, until the JIT works out that nobody needs the object.
The same loop over 10,000 risks, batch after batch, nothing changing but how often it has run:

```
batch  1  (calls   1- 20)   24.01 B per read
batch  2  (calls  21- 40)   24.01 B per read
batch  3  (calls  41- 60)    0.00 B per read   <- C2 has compiled it
...
batch 12  (calls 221-240)    0.00 B per read
```

So the honest formulation is neither "flattening is free" nor "reads cost an allocation": **a read costs one
buffered value until the method is C2-compiled, and nothing at all afterwards.** Here that is the first ~40 passes,
about 9.6 MB of young-gen garbage once per JVM, against 160,000 B and 10,000 objects saved for as long as the data
lives. An identity `Money` would be the opposite trade: a separate 16 B object per `Risk`, permanently, but free to
read from the very first call.

Where the trade goes the wrong way: code that never gets hot — CLI tools, serverless cold starts, start-up paths,
unit tests. That is also the correct reading of demo 24's `C1 only` column, which pins the code to C1 for good
with `-XX:TieredStopAtLevel=1` and so shows the permanent version of this transient cost.

**There is nothing to write around it.** Hiding the read inside the class does not help — measured with a
`long getCents() { return base.cents(); }` on `Risk`, so the `Money` never crosses a class boundary and the method
hands out a `long`:

| | C2 | C1 only |
|---|---|---|
| `r.base().cents()` — `Money` crosses the boundary | 0.00 B | 24.00 B |
| `r.getCents()` — read inside `Risk`, returns `long` | 0.00 B | 24.00 B |
| `r.getCentsViaAccessor()` — same, through `base()` | 0.00 B | 24.00 B |

The bytecode says why: `getfield base` on a flattened field produces a `Money` *reference* by definition, wherever
it appears. C1 implements that literally; C2 tracks the fields and never builds the object. So the boundary that
decides the cost is not the class, the method or the accessor — it is the compiler tier, and nothing in the source
can move it.

---

## Ideas not (yet) done

- **Scalarisation across calls** — JMH with `-prof gc`: a `@CompilerControl(DONT_INLINE)` method returning a
  value record versus an identity one. Worth doing precisely because demo 21 suggests the answer is not the
  obvious one on this build: C2 re-buffers a flat value that escapes a non-inlined call.
- **JDK classes that changed under your feet** — `isValue()` / `isFlatArray(new T[8])` for `Integer`, `Long`,
  `Optional`, `LocalDate`, `LocalTime`, `LocalDateTime` (8 + 7 B → not flat), `Duration`, `Instant`, `String`, `UUID`.
- **Tearing** — not demonstrable on this build: non-atomic flattening above 8 B is off even for null-restricted
  non-atomic arrays (the JDK-internal `@LooselyConsistentValue` is honoured only for boot-classpath classes).
- **Does `final` raise the field limit?** Answered in passing and worth a row in demo 08: no — plain `final` counts
  for nothing, because javac marks it neither strict nor trusted and reflection can still rewrite it. Only
  strictness (record components, value-class fields) lifts the 64-bit rule.
