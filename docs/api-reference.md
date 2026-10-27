# 2. API reference

All declarations live in `com.github.devlotfi.composeform`.

## `FormDefinition<S>`

```kotlin
abstract class FormDefinition<S>(
    var validateOnChange: Boolean = true,
    var validateOnBlur: Boolean = true
)
```

The base class of every form. `S` is the schema type used by your validation library.

### Constructor parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `validateOnChange` | `true` | Validate a field every time its `value` is assigned |
| `validateOnBlur` | `true` | Validate a field when `blur()` is called (usually from `Modifier.onBlur`) |

Both are `var`s, so they can be changed at runtime. They are also passed through your base class if you expose them:

```kotlin
abstract class KonformFormDefinition(
    validateOnChange: Boolean = true,
    validateOnBlur: Boolean = true
) : FormDefinition<Validation<*>>(validateOnChange, validateOnBlur) { /* ... */ }

class SearchForm : KonformFormDefinition(validateOnChange = false) { /* ... */ }
```

### Members

| Member | Signature | Description |
|--------|-----------|-------------|
| `fields` | `MutableMap<String, FormFieldData<*, *>>` | All registered fields, keyed by property name, in declaration order |
| `formField` | `fun <T> formField(initialValue: T, error: String? = null, touched: Boolean = false, schemaBlock: (() -> S)? = null)` | Property delegate provider used with `by` |
| `validateField` | `abstract fun <T> validateField(value: T, schema: S?): String?` | **You implement it.** Return an error message or `null` |
| `onSubmit` | `abstract fun onSubmit()` | **You implement it.** Called by `submit()` when the form is valid |
| `validate` | `fun validate(): Map<String, String>` | Validates every field, marks them touched, returns `fieldName -> error` for invalid ones |
| `submit` | `fun submit()` | `if (validate().isEmpty()) onSubmit()` |
| `reset` | `fun reset()` | Calls `reset()` on every field |

> `validate()`, `submit()` and `reset()` are **final** (Kotlin default). Do not try to override them. For nested forms and
> custom flows use the composition patterns in [Nested fields](nested-fields.md).

### `formField(...)`

```kotlin
val name by formField("")                                   // no schema
val age by formField(18) { /* schema */ }                   // with schema
val tags by formField(emptyList<String>()) { /* schema */ } // any type T
val nickname by formField<String?>(null)                    // nullable value type
```

| Parameter | Description |
|-----------|-------------|
| `initialValue: T` | Starting value and the value restored by `reset()`. Also fixes the type `T` of the field |
| `error: String?` | Initial error. **Note:** it is immediately overwritten by the validation that runs on creation (see below) |
| `touched: Boolean` | Initial touched state |
| `schemaBlock` | Lambda returning the schema. Evaluated **once**, when the form is constructed |

Because the schema block runs once at construction, it may capture other fields lazily (see
[cross-field validation](validation-behaviour.md#cross-field-validation)).

## `FormFieldData<T, S>`

What you get when you read a delegated property (`form.email`).

| Member | Type | Observable | Description |
|--------|------|:---:|-------------|
| `value` | `T` | yes | Current value. **Setting** it marks the field touched and validates if `validateOnChange` |
| `error` | `String?` | yes | Current error message, `null` when valid |
| `touched` | `Boolean` | yes | `true` after the first write, a blur, or a `validate()` on the form |
| `schema` | `S?` | yes | The schema of the field. It is a `var`, so it can be swapped at runtime |
| `initialValue` | `T` | no | Value used by `reset()`. It is a `var` |
| `blur()` | `Unit` | | Marks touched, validates if `validateOnBlur` |
| `validate()` | `Unit` | | Runs `validateField(value, schema)` and stores the result in `error` |
| `reset()` | `Unit` | | `value = initialValue; error = null; touched = false` |

### Lifecycle details

| Event | `value` | `error` | `touched` | Validates? |
|-------|:---:|:---:|:---:|:---:|
| Field creation | `initialValue` | result of validation (initial `error` argument is discarded) | as passed (default `false`) | always |
| `field.value = x` | `x` | updated if `validateOnChange` | `true` | if `validateOnChange` |
| `field.blur()` | unchanged | updated if `validateOnBlur` | `true` | if `validateOnBlur` |
| `field.validate()` | unchanged | updated | unchanged | always |
| `form.validate()` / `form.submit()` | unchanged | updated | `true` for all fields | always |
| `field.reset()` | `initialValue` | `null` | `false` | transiently (see gotchas) |

### Swapping a schema at runtime

```kotlin
// Make a field required only when a checkbox is ticked
form.companyName.schema = if (form.isCompany.value) requiredSchema else null
form.companyName.validate()
```

## `FormField<T, S>`

The `ReadOnlyProperty` returned by the delegate. It looks up the registered `FormFieldData` by property name:

```kotlin
thisRef.fields[property.name] as FormFieldData<T, S>
```

You never instantiate it yourself. Consequences:

- The Kotlin **property name is the field key** (`form.fields["email"]`, keys of `validate()`).
- Properties must be declared with `by formField(...)`, otherwise they are not registered.
- Renaming a property changes its key in `validate()` results.

## `Modifier.onBlur`

```kotlin
fun Modifier.onBlur(onBlur: () -> Unit): Modifier
```

Invokes `onBlur` when the element **had focus and lost it**. It uses `onFocusChanged` internally and remembers whether
the element was focused, so it does not fire on first composition.

```kotlin
OutlinedTextField(
    value = form.email.value,
    onValueChange = { form.email.value = it },
    modifier = Modifier.onBlur { form.email.blur() }
)
```

Apply it to the **focusable** composable (the text field itself), or to a parent only if the parent actually receives
focus changes. When in doubt, put it directly on the text field.

## Reading the whole form

```kotlin
val errors: Map<String, String> = form.validate()   // side effects: marks all touched
val values = form.fields.mapValues { it.value.value }   // Map<String, Any?>

val hasErrors = form.fields.values.any { it.error != null }  // no side effects
val isDirty = form.fields.values.any { it.value != it.initialValue }
```

`isDirty` and `hasErrors` are plain reads of Compose state, so using them in a composable makes it recompose
when anything changes:

```kotlin
Button(onClick = { form.submit() }, enabled = !hasErrors) { Text("Save") }
```
