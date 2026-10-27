# 3. Setting up a validator

compose-form does **not** ship a validation engine. Instead, `FormDefinition<S>` asks you for one function:

```kotlin
abstract fun <T> validateField(value: T, schema: S?): String?
```

| Part | Meaning |
|------|---------|
| `value: T` | The current value of the field |
| `schema: S?` | The schema declared in the field's `formField { ... }` block, or `null` when none was declared |
| return | An error message to display, or `null` when the value is valid |

The type parameter `S` of `FormDefinition<S>` is **whatever type your validation library uses to describe rules**.
You create **one abstract base class per validation library**, then every form in your app extends it.

```
FormDefinition<S>
   └── KonformFormDefinition   (S = Validation<*>)
          ├── LoginForm
          └── ProfileForm
```

Rules of thumb when implementing `validateField`:

- Return `null` when `schema == null`.
- Return **one** string. If your validator produces many errors, pick the first (or join them).
- Never throw for invalid data. Return a message instead.
- Make the function side-effect free: it runs on every keystroke by default.

---

## Konform

[Konform](https://github.com/konform-kt/konform) is a multiplatform, type-safe validation DSL.

### Base class

```kotlin
import com.github.devlotfi.composeform.FormDefinition
import io.konform.validation.Validation

abstract class KonformFormDefinition : FormDefinition<Validation<*>>() {
    @Suppress("UNCHECKED_CAST")
    override fun <T> validateField(value: T, schema: Validation<*>?): String? {
        val result = (schema as? Validation<T>)?.validate(value)
        val error = result?.errors?.firstOrNull()
        return if (error != null) error.toString() else null
    }
}
```

How it works:

1. `schema` is stored as `Validation<*>` because a form holds fields of different types.
2. The unchecked cast to `Validation<T>` is safe as long as the schema you declare for a field has the same type
   as the field value (`formField("") { Validation<String> { ... } }`).
3. `result?.errors` is `null`/empty when valid. Only the first error is displayed.

> **Tip.** Depending on your Konform version, `ValidationError.toString()` may include the data path in front of the
> message. If you only want the message text, return `error.message` instead of `error.toString()`.

### Using it

```kotlin
import io.konform.validation.Validation
import io.konform.validation.constraints.*

class RegisterForm : KonformFormDefinition() {

    val username by formField("") {
        Validation<String> {
            minLength(3) hint "At least 3 characters"
            maxLength(20) hint "At most 20 characters"
            pattern("^[a-zA-Z0-9_]+$") hint "Letters, digits and underscores only"
        }
    }

    val age by formField(18) {
        Validation<Int> {
            minimum(18) hint "You must be an adult"
            maximum(120) hint "Please enter a valid age"
        }
    }

    val acceptTerms by formField(false) {
        Validation<Boolean> {
            constrain("You must accept the terms") { it }
        }
    }

    override fun onSubmit() { /* ... */ }
}
```

### Konform notes

| Situation | Approach |
|-----------|----------|
| Custom rule | `constrain("message") { value -> predicate }` |
| Optional field (`String?`) | `formField<String?>(null) { Validation<String?> { ifPresent { minLength(3) } } }` |
| Cross-field rule | `constrain("Passwords do not match") { it == password.value }`, see [cross-field validation](validation-behaviour.md#cross-field-validation) |
| Validating a whole object (nested) | `Validation<Address> { Address::city { minLength(1) } }`, see [nested fields](nested-fields.md) |
| Validating a list | `Validation<List<String>> { minItems(1); onEach { minLength(2) } }`, see [lists](lists.md) |

> The exact constraint names and `hint` syntax follow your Konform version. Check the Konform docs if one of the
> constraints above does not compile for you.

---

## Kanyon

[Kanyon](https://devlotfi.github.io/kanyon/llms.txt) is a declarative, pure Kotlin validation library for individual
values and object properties. Its entry point for untyped values is `FieldSchema`, whose `validate(value: Any?)`
returns `null` for valid values or a human-readable error `String`. That maps one-to-one onto `validateField`.

### Base class

```kotlin
import com.github.devlotfi.composeform.FormDefinition
import com.github.devlotfi.kanyon.FieldSchema   // adjust to Kanyon's real package

abstract class KanyonFormDefinition : FormDefinition<FieldSchema>() {
    override fun <T> validateField(value: T, schema: FieldSchema?): String? {
        return schema?.validate(value)
    }
}
```

No cast is needed: `FieldSchema` validates `Any?`, and Kanyon already returns a single `String?`
(it stops at the first failing rule).

### Using it

Build every field schema with `FieldSchema()`, chain a **type method** (`string()`, `int()`, `boolean()`, ...) and then
the rules, and finish with `.build()`.

```kotlin
class RegisterForm : KanyonFormDefinition() {

    val username by formField("") {
        FieldSchema()
            .string()
            .min(3, "At least 3 characters")
            .max(20, "At most 20 characters")
            .build()
    }

    val email by formField("") {
        FieldSchema()
            .string()
            .notBlank("Email is required")
            .email("Invalid email address")
            .build()
    }

    val age by formField(18) {
        FieldSchema()
            .int()
            .gte(18, "You must be an adult")
            .lte(120)
            .build()
    }

    val acceptTerms by formField(false) {
        FieldSchema().boolean().eq(true, "You must accept the terms").build()
    }

    val nickname by formField<String?>(null) {
        FieldSchema().string().min(2).max(16).nullable().build()
    }

    override fun onSubmit() { /* ... */ }
}
```

### Kanyon notes

| Situation | Approach |
|-----------|----------|
| Type check is **strict** | Use `int()` for `Int`, `long()` for `Long`, `double()` for `Double`. An `Int` will not pass `double()` |
| `null` values | Rejected by default. Add `.nullable()` and declare the field as `formField<String?>(null)` |
| Custom rule | `.validateHandler({ value -> predicate }, "message")` |
| Cross-field rule | `.validateHandler({ it == password.value }, "Passwords do not match")` |
| Nested object | `FieldSchema().isOf<Address>().validateHandler({ it.valid() }, "Address is invalid").build()` |
| List | `FieldSchema().isOf<List<*>>().validateHandler({ it.isNotEmpty() }, "At least one item").build()` |

> Kanyon validates nested objects, lists and maps through `isOf<...>()` plus a custom `validateHandler`, and reports
> one message per field. See [nested fields](nested-fields.md) and [lists](lists.md) for how this combines
> with compose-form.

---

## Writing your own validator

You do not need a library at all. `S` can be any type. Here is a tiny rule-based validator:

```kotlin
fun interface Rule<T> {
    fun check(value: T): String?        // null = valid
}

object Rules {
    fun required(msg: String = "Required") = Rule<String> { if (it.isBlank()) msg else null }
    fun minLength(n: Int, msg: String = "Min $n characters") = Rule<String> { if (it.length < n) msg else null }
    fun <T> all(vararg rules: Rule<T>) = Rule<T> { v -> rules.firstNotNullOfOrNull { it.check(v) } }
}

abstract class RuleFormDefinition : FormDefinition<Rule<*>>() {
    @Suppress("UNCHECKED_CAST")
    override fun <T> validateField(value: T, schema: Rule<*>?): String? =
        (schema as? Rule<T>)?.check(value)
}

class NameForm : RuleFormDefinition() {
    val name by formField("") { Rules.all(Rules.required(), Rules.minLength(3)) }
    override fun onSubmit() {}
}
```

### Even simpler: lambdas as schemas

```kotlin
abstract class LambdaFormDefinition : FormDefinition<(Any?) -> String?>() {
    override fun <T> validateField(value: T, schema: ((Any?) -> String?)?): String? = schema?.invoke(value)
}

class QuickForm : LambdaFormDefinition() {
    val name by formField("") { { v -> if ((v as String).isBlank()) "Required" else null } }
    override fun onSubmit() {}
}
```

### Other libraries

The same pattern works for any validator. Choose `S` as the library's schema/validator type and delegate:

| Library style | `S` | `validateField` body |
|---------------|-----|----------------------|
| Returns a list of errors | the validator type | `validator.validate(value).firstOrNull()?.message` |
| Throws on invalid input | the validator type | `try { validator.validate(value); null } catch (e: ValidationException) { e.message }` |
| Boolean predicate | `Pair<(T) -> Boolean, String>`-like | `if (predicate(value)) null else message` |

---

## Localised / customised messages

Return whatever string you want. You can map error keys to localised strings inside `validateField`:

```kotlin
abstract class I18nKonformFormDefinition(private val t: (String) -> String) : FormDefinition<Validation<*>>() {
    @Suppress("UNCHECKED_CAST")
    override fun <T> validateField(value: T, schema: Validation<*>?): String? {
        val error = (schema as? Validation<T>)?.validate(value)?.errors?.firstOrNull() ?: return null
        return t(error.message)          // error.message used as a translation key
    }
}
```

## Testing your base class

Because `validateField` is a plain function, it is trivial to unit test through a tiny form:

```kotlin
class TestForm : KonformFormDefinition() {
    val name by formField("") { Validation<String> { minLength(3) hint "short" } }
    override fun onSubmit() {}
}

@Test fun shortNameIsInvalid() {
    val form = TestForm()
    form.name.value = "ab"
    assertTrue(form.name.error!!.contains("short"))   // use assertEquals if you return error.message
    form.name.value = "abc"
    assertNull(form.name.error)
}
```

(`FormFieldData` uses Compose `mutableStateOf`, which works in plain JVM unit tests, no UI required.)
