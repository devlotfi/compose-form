# compose-form

A small, **validation-agnostic** form state library for Jetpack / Compose Multiplatform, inspired by Formik.

You describe a form as a class, each field becomes an observable piece of Compose state
(`value`, `error`, `touched`), and you plug in *any* validation library by implementing a single method.

```kotlin
class LoginForm(private val onValid: (String, String) -> Unit) : KanyonFormDefinition() {
    val email by formField("") { FieldSchema().string().notBlank("Required").email("Invalid email").build() }
    val password by formField("") { FieldSchema().string().min(8, "At least 8 characters").build() }

    override fun onSubmit() = onValid(email.value, password.value)
}

@Composable
fun LoginScreen() {
    val form = remember { LoginForm { email, password -> /* call your API */ } }

    OutlinedTextField(
        value = form.email.value,
        onValueChange = { form.email.value = it },
        isError = form.email.touched && form.email.error != null,
        supportingText = { if (form.email.touched) form.email.error?.let { Text(it) } },
        modifier = Modifier.onBlur { form.email.blur() }
    )
    // ... password field ...
    Button(onClick = { form.submit() }) { Text("Log in") }
}
```

## Features

- Declarative field definitions with property delegates (`by formField(...)`)
- Reactive `value`, `error` and `touched` per field, backed by Compose `mutableStateOf`
- Validate on change, on blur, on submit, or manually
- `Modifier.onBlur` to detect when a field loses focus
- Works with **any** validator: Konform, Kanyon, your own rules, ...
- Nested objects, lists, and lists of sub-forms through simple composition patterns
- Zero dependency on `ViewModel`, coroutines or any UI toolkit besides Compose runtime / UI

## Documentation

| # | File | What you will learn |
|---|------|---------------------|
| 1 | [Getting started](getting-started.md) | Core concepts, your first form, wiring it to the UI |
| 2 | [API reference](api-reference.md) | `FormDefinition`, `FormFieldData`, `FormField`, `Modifier.onBlur` in detail |
| 3 | [Setting up a validator](validators.md) | The `validateField` contract, **Konform**, **Kanyon**, writing your own |
| 4 | [Validation behaviour](validation-behaviour.md) | `validateOnChange` / `validateOnBlur`, touched, showing errors, cross-field rules, server errors |
| 5 | [Nested fields](nested-fields.md) | Object-valued fields and nested sub-forms |
| 6 | [Lists of fields](lists.md) | Primitive lists, object lists, dynamic lists of sub-forms |
| 7 | [Compose UI patterns](compose-ui-patterns.md) | Reusable field components, ViewModel usage, async submit |
| 8 | [Recipes, gotchas & FAQ](recipes-and-gotchas.md) | Full examples and the sharp edges to know about |

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.devlotfi:composeform:<version>")
}
```

> Replace the coordinates/version with the ones you actually publish.
> Package used in all examples: `com.github.devlotfi.composeform`.

## Mental model in one minute

```
FormDefinition<S>            S = the type of your validation "schema"
 ├─ validateField(value, S?) ← you implement this (once per validator library)
 ├─ onSubmit()               ← you implement this (once per form)
 └─ fields: Map<String, FormFieldData>
        └─ FormFieldData<T, S>: value · error · touched · schema · blur() · validate() · reset()
```

1. Pick a base class for your validator (`KonformFormDefinition`, `KanyonFormDefinition`, or your own).
2. Subclass it for each form and declare fields with `formField(initialValue) { schema }`.
3. Read/write `form.someField.value` in your composables, render `error` when `touched`.
4. Call `form.submit()`. It validates every field and only calls `onSubmit()` when all are valid.
