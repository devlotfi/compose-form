# 4. Validation behaviour

This page explains **when** validation runs and **how** to show the results.

## When does a field validate?

| Trigger | Condition |
|---------|-----------|
| Form construction | Always (each field validates once when created) |
| `field.value = x` | `validateOnChange == true` (default) |
| `field.blur()` | `validateOnBlur == true` (default) |
| `field.validate()` | Always |
| `form.validate()` / `form.submit()` | Always, for every field |

### Validation strategies

```kotlin
// 1. Default: validate on change AND on blur (instant feedback)
class A : KonformFormDefinition()

// 2. On blur only: don't nag while the user is still typing
class B : KonformFormDefinition(validateOnChange = false)

// 3. On submit only: nothing validates until submit()
class C : KonformFormDefinition(validateOnChange = false, validateOnBlur = false)
```

> This requires your base class to forward the two constructor parameters (see
> [API reference](api-reference.md#constructor-parameters)). Otherwise change them in an `init` block:
> `init { validateOnChange = false }`.

With strategy 2 or 3 the `error` of a field is `null` until the next validation, even if the value is invalid.
`submit()` always validates everything first, so invalid data never reaches `onSubmit()`.

## What does `touched` mean?

`touched` records "the user has interacted with this field". It becomes `true` when:

- the value is assigned (typing),
- `blur()` is called,
- `form.validate()` / `form.submit()` runs (all fields at once, so every error becomes visible after a failed submit).

It becomes `false` again on `reset()`.

### Showing errors: the standard pattern

```kotlin
val showError = field.touched && field.error != null
OutlinedTextField(
    // ...
    isError = showError,
    supportingText = { if (showError) Text(field.error!!) }
)
```

Because every field validates once at construction, `error` is already populated for an empty required field.
Gating on `touched` is what keeps the form calm until the user interacts.

A tiny helper removes the repetition:

```kotlin
val FormFieldData<*, *>.visibleError: String?
    get() = if (touched) error else null

// usage
isError = form.email.visibleError != null
supportingText = { form.email.visibleError?.let { Text(it) } }
```

## Validating manually

```kotlin
form.email.validate()          // one field, no touched change
val errors = form.validate()   // all fields, marks all as touched; returns Map<fieldName, error>

if (errors.isNotEmpty()) {
    println(errors)            // {email=Invalid email address, password=Password must be at least 8 characters}
}
```

`form.validate()` returns only fields that have an error, so `isEmpty()` means the form is valid.

## Submitting

```kotlin
form.submit()   // = if (form.validate().isEmpty()) form.onSubmit()
```

`onSubmit()` is a plain, non-suspending function. For async work, see
[Compose UI patterns](compose-ui-patterns.md#async-submit-and-loading-state).

## Cross-field validation

The schema block is evaluated once at construction, but the lambdas **inside** the schema run at validation time,
so they can read other fields. Declare the field you depend on **before** the field that reads it.

```kotlin
class SignUpForm : KonformFormDefinition() {
    val password by formField("") {
        Validation<String> { minLength(8) hint "At least 8 characters" }
    }

    val confirmPassword by formField("") {
        Validation<String> {
            constrain("Passwords do not match") { it == password.value }
        }
    }

    override fun onSubmit() {}
}
```

Same thing with Kanyon:

```kotlin
val confirmPassword by formField("") {
    FieldSchema().string()
        .validateHandler({ it == password.value }, "Passwords do not match")
        .build()
}
```

### Re-validate dependants

Changing `password` does **not** automatically re-validate `confirmPassword`. Do it yourself:

```kotlin
OutlinedTextField(
    value = form.password.value,
    onValueChange = {
        form.password.value = it
        if (form.confirmPassword.touched) form.confirmPassword.validate()
    },
    // ...
)
```

Or encapsulate it in the form:

```kotlin
fun setPassword(value: String) {
    password.value = value
    if (confirmPassword.touched) confirmPassword.validate()
}
```

## Conditional validation

Swap the schema at runtime and re-validate:

```kotlin
class ShippingForm : KonformFormDefinition() {
    val sameAsBilling by formField(true)

    val address by formField("") {
        Validation<String> { minLength(5) hint "Address too short" }
    }

    fun onSameAsBillingChanged(checked: Boolean) {
        sameAsBilling.value = checked
        if (checked) {
            address.schema = null        // no rules: always valid
        } else {
            address.schema = addressSchema
        }
        address.validate()
    }

    private val addressSchema = Validation<String> { minLength(5) hint "Address too short" }

    override fun onSubmit() {}
}
```

Alternative with no schema swapping: put the condition inside the rule
(`constrain("Required") { sameAsBilling.value || it.length >= 5 }`).

## Server-side errors

`error` is a mutable property, so you can set it from your API response:

```kotlin
fun onServerError(response: ApiError) {
    if (response.code == "EMAIL_TAKEN") {
        email.error = "This email is already registered"
        email.touched = true
    }
}
```

The error stays until the next validation of that field (the next keystroke with `validateOnChange`), which is
usually exactly the behaviour you want.

> Server-side errors are not visible to `form.validate()`: calling `submit()` again re-runs local validation and
> overwrites them.

## Resetting and "dirty" state

```kotlin
form.reset()                                       // all fields back to initial values, errors cleared
form.email.reset()                                 // single field

val isDirty = form.fields.values.any { it.value != it.initialValue }
```

To make the current values the new baseline (for instance after a successful save):

`initialValue` is a typed `var`, and `fields` is typed `FormFieldData<*, *>`, so assign through a typed helper:

```kotlin
fun <T, S> FormFieldData<T, S>.commit() { initialValue = value }

fun commitAll() {
    email.commit(); password.commit()
}
```

## Pre-filling a form (edit screens)

Pass the initial values through the constructor so that `reset()` returns to the loaded data:

```kotlin
class ProfileForm(user: User) : KonformFormDefinition() {
    val name by formField(user.name) { Validation<String> { minLength(1) hint "Required" } }
    val bio by formField(user.bio)
    override fun onSubmit() {}
}

val form = remember(user.id) { ProfileForm(user) }
```
