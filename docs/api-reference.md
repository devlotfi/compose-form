# 8. API reference

## `FormDefinition<S>`

```kotlin
abstract class FormDefinition<S>(
    var validateOnChange: Boolean = true,
    var validateOnBlur: Boolean = true,
    val root: FormDefinition<S>? = null
)
```

| Member | Signature | Description |
|---|---|---|
| `validateOnChange` | `var Boolean` | Validate a field whenever its `value` is set. Default `true`. |
| `validateOnBlur` | `var Boolean` | Validate a field when `blur()` is called. Default `true`. |
| `root` | `val FormDefinition<S>?` | The parent form this form registers its fields with. `null` for top-level forms. |
| `fields` | `MutableSet<FormFieldData<*, *>>` | Fields registered on this form (own fields plus those of sub-forms that name it as root). Not observable by Compose. |
| `formField(...)` | see below | Delegate factory that declares a field. |
| `validateField(value, schema)` | `abstract fun <T> validateField(value: T, schema: S?): String?` | **You implement this** (usually via an adapter). Returns an error message or `null`. |
| `onSubmit()` | `open fun onSubmit()` | Called by `submit()` when the form is valid. Default does nothing. |
| `validate()` | `fun validate(): Boolean` | Validates all `fields`; returns `true` if none has an error. |
| `submit()` | `fun submit()` | `if (validate()) onSubmit()`. |
| `reset()` | `fun reset()` | Resets every field in `fields`. |
| `dispose()` | `fun dispose()` | Removes this form's fields from `root.fields`. No effect when `root == null`. |

### `formField`

```kotlin
fun <T> formField(
    initialValue: T,
    error: String? = null,
    schemaBlock: (() -> S)? = null
): PropertyDelegateProvider<FormDefinition<S>, FormField<T, S>>
```

Use with `by`:

```kotlin
val email by formField("") { /* schema of type S */ }
val nickname by formField<String?>(null)    // explicit type argument when the initial value is null
```

The property's type is `FormFieldData<T, S>`.

## `FormFieldData<T, S>`

```kotlin
class FormFieldData<T, S>(
    val initialValue: T,
    error: String?,
    schema: S?,
    val thisRef: FormDefinition<S>
)
```

| Member | Type | Description |
|---|---|---|
| `initialValue` | `T` | Value given at declaration. |
| `value` | `var T` | Current value (Compose state). Setting it validates when `thisRef.validateOnChange`. |
| `error` | `var String?` | Current error (Compose state). Assignable. |
| `schema` | `var S?` | Current schema (Compose state). Assignable. `null` = no validation. |
| `thisRef` | `FormDefinition<S>` | The form that declared the field. Its timing flags and `validateField` are used. |
| `blur()` | `fun` | Validates if `thisRef.validateOnBlur`. |
| `validate()` | `fun` | Runs `thisRef.validateField(value, schema)` and stores the result in `error`. |
| `reset()` | `fun` | `value = initialValue; error = null`. |

## `FormField<T, S>`

Internal `ReadOnlyProperty` returned by `formField`. It exposes the `FormFieldData` as the delegated property's value. You normally never use it directly.

## `Modifier.onBlur`

```kotlin
fun Modifier.onBlur(onBlur: () -> Unit): Modifier
```

Invokes `onBlur` when the element goes from focused to unfocused. Does not fire on initial composition.

## Provided adapters

### `KonformFormDefinition`

```kotlin
abstract class KonformFormDefinition(parent: FormDefinition<Validation<*>>? = null) :
    FormDefinition<Validation<*>>(root = parent)
```

Schema type: `io.konform.validation.Validation<*>`. Reports the first Konform error message.

### `KanyonFormDefinition`

```kotlin
abstract class KanyonFormDefinition(
    parent: FormDefinition<FieldSchema>? = null,
    validateOnChange: Boolean = true,
    validateOnBlur: Boolean = true
) : FormDefinition<FieldSchema>(validateOnChange, validateOnBlur, parent)
```

Schema type: Kanyon's `FieldSchema`. Delegates to `schema.validate(value)`. (Pass the base-class arguments by name; see [Validation setup](validation-setup.md#kanyon).)

## Lifecycle summary

```text
construct form
   └─ formField(...) delegates run → each FormFieldData is created and registered
user types          → field.value = x   → (validateOnChange) validate()
user leaves field   → field.blur()      → (validateOnBlur)  validate()
user presses submit → form.submit()     → validate() all → onSubmit() if valid
user presses reset  → form.reset()      → every field: value = initial, error = null
item removed        → subForm.dispose() → fields unregistered from root
```
