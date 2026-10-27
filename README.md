<img src="https://raw.githubusercontent.com/devlotfi/compose-form/master/github-assets/github-banner.png">


# Compose Form

[![JitPack](https://jitpack.io/v/devlotfi/compose-form.svg)](https://jitpack.io/#devlotfi/compose-form)

A small, validation-agnostic form state library for Jetpack Compose and Compose Multiplatform. Define forms as classes, expose observable field state to your UI, and connect any validation library through a single `validateField` method.

## Features

- Declarative field definitions with Kotlin property delegates
- Reactive `value`, `error`, and `touched` state backed by Compose
- Validate fields on change, blur, submit, or manually
- Integrate with any validation library—or write your own rules
- Compose-friendly nested fields, lists, and sub-forms
- `Modifier.onBlur` for detecting when a field loses focus

## Installation

Compose Form is published on [JitPack](https://jitpack.io/#devlotfi/compose-form).

## Documentation

- [Docs](https://github.com/devlotfi/compose-form/blob/master/docs/intro.md)
- [llms.txt](https://devlotfi.github.io/compose-form/llms.txt)

## Quick start

```kotlin
class LoginForm(private val onValid: (String) -> Unit) : FormDefinition<Unit>() {
    val email by formField("")

    override fun <T> validateField(value: T, schema: Unit?): String? =
        if (value is String && value.isBlank()) "Email is required" else null

    override fun onSubmit() = onValid(email.value)
}
```

In a composable, remember the form, bind its field state to a text field, and submit:

```kotlin
val form = remember { LoginForm { email -> /* handle valid email */ } }

OutlinedTextField(
    value = form.email.value,
    onValueChange = { form.email.value = it },
    isError = form.email.touched && form.email.error != null,
    modifier = Modifier.onBlur { form.email.blur() }
)
Button(onClick = { form.submit() }) {
    Text("Continue")
}
```