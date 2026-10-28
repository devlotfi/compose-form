# 2. Validation setup

compose-form does **not** ship with a validator. It only needs to know one thing: *"given a value and a schema, what is the error message (or `null`)?"*

That question is the single abstract function on `FormDefinition<S>`:

```kotlin
abstract fun <T> validateField(value: T, schema: S?): String?
```

- `S` is the schema type of your validation library.
- Return `null` when the value is valid, or the error message to display.
- `schema` is `null` when the field was declared without a schema block. Return `null` in that case (no rules means always valid).

The `formField { ... }` schema block must return an `S`. That is how the schema ends up typed as your library's type.

```text
formField("") { <S> }  ──►  FormFieldData.schema : S?
                                   │
                    field.validate()
                                   ▼
          FormDefinition.validateField(value, schema) : String?
                                   ▼
                          FormFieldData.error
```

You write the adapter **once per validation library**, then every form in your app extends it.

---

## Konform

[Konform](https://github.com/konform-kt/konform) validations are `Validation<T>` objects.

### The adapter

```kotlin
abstract class KonformFormDefinition(parent: FormDefinition<Validation<*>>? = null) :
    FormDefinition<Validation<*>>(root = parent) {

    @Suppress("UNCHECKED_CAST")
    override fun <T> validateField(value: T, schema: Validation<*>?): String? {
        val result = (schema as? Validation<T>)?.validate(value)
        val error = result?.errors?.firstOrNull()
        return if (error != null) error.message else null
    }
}
```

What it does:

1. Casts the stored `Validation<*>` back to `Validation<T>` (safe as long as the schema you declare matches the field type).
2. Runs it.
3. Shows the **first** error only. If you want to show all errors joined, replace `firstOrNull()` with `result?.errors?.joinToString("\n") { it.message }`.

> Constructor note: `parent` is how a sub-form registers with its parent form. Top-level forms use the default `null`. See [Nested forms](nested-forms.md).

### Using it

```kotlin
import io.konform.validation.Validation
import io.konform.validation.constraints.*

class ProfileForm : KonformFormDefinition() {

    val name by formField("") {
        Validation<String> {
            notBlank() hint "Name is required"
            minLength(2) hint "At least 2 characters"
            maxLength(40) hint "At most 40 characters"
        }
    }

    val age by formField(18) {
        Validation<Int> {
            minimum(13) hint "You must be at least 13"
            maximum(120) hint "Please enter a real age"
        }
    }

    val acceptTerms by formField(false) {
        Validation<Boolean> {
            constrain("You must accept the terms") { it }
        }
    }
}
```

Type rule: **the type argument of `Validation<...>` must equal the field type.** `formField("")` is a `String` field, so use `Validation<String>`. A mismatch compiles but the cast in `validateField` yields `null` for the wrong type and the field is silently treated as valid.

### Configuring timing with Konform

`KonformFormDefinition` does not take timing arguments. Use an `init` block:

```kotlin
class ProfileForm : KonformFormDefinition() {
    init {
        validateOnChange = false
        validateOnBlur = true
    }
    // ...
}
```

---

## Kanyon

[Kanyon](https://devlotfi.github.io/kanyon/llms.txt) is a declarative Kotlin validation library built on composable `FieldSchema` objects. A built schema is a `FieldSchema` whose `validate(value)` returns `null` (valid) or an error message `String`, which fits `validateField` directly.

### The adapter

```kotlin
abstract class KanyonFormDefinition(
    parent: FormDefinition<FieldSchema>? = null,
    validateOnChange: Boolean = true,
    validateOnBlur: Boolean = true
) : FormDefinition<FieldSchema>(
    validateOnChange = validateOnChange,
    validateOnBlur = validateOnBlur,
    root = parent
) {
    override fun <T> validateField(value: T, schema: FieldSchema?): String? {
        return schema?.validate(value)
    }
}
```

> `FormDefinition`'s constructor order is `(validateOnChange, validateOnBlur, root)`, so pass the arguments **by name** as above. Passing `parent` positionally as the first argument would not compile.

Unlike the Konform adapter, this one exposes `validateOnChange` and `validateOnBlur` as constructor parameters, so sub-classes can configure timing without an `init` block:

```kotlin
class ProfileForm : KanyonFormDefinition(validateOnChange = false) { /* ... */ }
```

### Using it

Build every schema through `FieldSchema()` and finish with `.build()`:

```kotlin
import dev.devlotfi.kanyon.FieldSchema   // adjust to Kanyon's actual package

class ProfileForm : KanyonFormDefinition() {

    val name by formField("") {
        FieldSchema()
            .string()
            .notBlank("Name is required")
            .min(2, "At least 2 characters")
            .max(40, "At most 40 characters")
            .build()
    }

    val email by formField("") {
        FieldSchema()
            .string()
            .notBlank("Email is required")
            .email("Invalid email")
            .build()
    }

    val age by formField(18) {
        FieldSchema()
            .int("Age must be a whole number")
            .gte(13, "You must be at least 13")
            .lte(120, "Please enter a real age")
            .build()
    }

    val acceptTerms by formField(false) {
        FieldSchema()
            .boolean()
            .eq(true, "You must accept the terms")
            .build()
    }
}
```

Kanyon-specific rules to keep in mind (from the Kanyon docs):

- **Types are strict.** `int()` does not accept a `Long` or `Double`. Make the field's Kotlin type match the schema method (`formField(18)` is `Int` so use `.int()`; `formField(18L)` needs `.long()`).
- **`null` is rejected by default.** For optional fields use a nullable field type and call `nullable()`:

```kotlin
val nickname by formField<String?>(null) {
    FieldSchema().string().min(2).max(16).nullable().build()
}
```

- **One type method per `FieldSchema`.** Do not chain `.string().int()`.
- Always call `.build()`; the form needs a `FieldSchema`, not an intermediate typed schema.

See the Kanyon docs for the full rule list (identifiers, network, dates, finance, custom handlers, ...): <https://devlotfi.github.io/kanyon/llms.txt>.

---

## Writing your own adapter

Any validation approach works, even a plain lambda. The steps are always the same:

1. Decide what `S` is.
2. Extend `FormDefinition<S>(root = parent)`.
3. Implement `validateField`.

### Example: plain lambdas as schemas

```kotlin
typealias Rule = (Any?) -> String?

abstract class LambdaFormDefinition(parent: FormDefinition<Rule>? = null) :
    FormDefinition<Rule>(root = parent) {

    override fun <T> validateField(value: T, schema: Rule?): String? =
        schema?.invoke(value)
}

class QuickForm : LambdaFormDefinition() {
    val name by formField("") {
        { v: Any? -> if ((v as String).isBlank()) "Required" else null }
    }
}
```

### Example: several rules, first failure wins

```kotlin
typealias Check<T> = (T) -> String?

class Rules<T>(private val checks: List<Check<T>>) {
    fun validate(value: T): String? = checks.firstNotNullOfOrNull { it(value) }
}

fun <T> rules(vararg checks: Check<T>) = Rules(checks.toList())

abstract class RulesFormDefinition(parent: FormDefinition<Rules<*>>? = null) :
    FormDefinition<Rules<*>>(root = parent) {

    @Suppress("UNCHECKED_CAST")
    override fun <T> validateField(value: T, schema: Rules<*>?): String? =
        (schema as? Rules<T>)?.validate(value)
}

class QuickForm : RulesFormDefinition() {
    val name by formField("") {
        rules<String>(
            { if (it.isBlank()) "Required" else null },
            { if (it.length < 3) "Too short" else null }
        )
    }
}
```

### Adapter checklist

| Requirement | Why |
|---|---|
| Constructor takes `parent: FormDefinition<S>? = null` and passes it as `root = parent` | Lets sub-forms register their fields with the parent form. |
| Handle `schema == null` | Fields without a schema block must not crash. |
| Return **`null`** for valid | Anything non-null is treated as an error and blocks `submit()`. |
| Return a **display-ready** message | The string is shown as-is (`field.error`). |
| Do not throw | An exception inside `validateField` propagates out of `field.value = ...`. |

---

## Mixing validators?

All fields in one form share the same `S`, and sub-forms must share their parent's `S`. If you need two validators in one app, use different adapters in different forms. Keep each form tree on one adapter.

## Next

- [Fields and state](fields.md)
