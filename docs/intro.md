# compose-form

A small, **validation-agnostic** form state library for Jetpack Compose / Compose Multiplatform, in the spirit of Formik.

You describe a form as a plain Kotlin class. Each field is a property delegate that holds its own **value**, **error** and **schema**. Compose reads those properties and recomposes automatically; there is no `StateFlow` plumbing, no reducers and no form-wide `Map<String, Any>`.

```kotlin
class LoginForm : KonformFormDefinition() {
    val email by formField("") {
        Validation<String> { notBlank() }
    }
    val password by formField("") {
        Validation<String> { minLength(8) }
    }

    override fun onSubmit() {
        println("Logging in as ${email.value}")
    }
}

@Composable
fun LoginScreen() {
    val form = remember { LoginForm() }

    Column {
        OutlinedTextField(
            modifier = Modifier.onBlur { form.email.blur() },
            value = form.email.value,
            onValueChange = { form.email.value = it },
            isError = form.email.error != null,
            supportingText = form.email.error?.let { { Text(it) } }
        )
        // ... password field ...
        Button(onClick = { form.submit() }) { Text("Log in") }
    }
}
```

## Why compose-form?

| Feature | Notes |
|---|---|
| Typed fields | `FormFieldData<String, *>`, `FormFieldData<Int, *>`, `FormFieldData<List<String>, *>`: the value type is whatever you declare. |
| Validation agnostic | Plug in Konform, Kanyon, or your own validator by implementing one function (`validateField`). |
| Validate on change / on blur / on submit | Configure per form. |
| Nested forms | Group fields into sub-forms (`AddressForm`, `ContactForm`, ...). |
| Dynamic lists | Add and remove sub-forms at runtime with `mutableStateListOf`. |
| Compose-native state | Values and errors are Compose `MutableState`; no extra collectors needed. |
| Multiplatform friendly | Pure Kotlin + Compose; no platform APIs. |

## Documentation

Read in order the first time, then jump around.

| # | Page | What you will learn |
|---|---|---|
| 1 | [Getting started](getting-started.md) | Core concepts, your first form, wiring it to Compose, the `onBlur` modifier |
| 2 | [Validation setup](validation-setup.md) | The `FormDefinition<S>` contract, **Konform**, **Kanyon**, and writing your own adapter |
| 3 | [Fields and state](fields.md) | `FormFieldData` API, field types (text, number, checkbox, enum, list), validation timing, dynamic schemas |
| 4 | [Nested forms](nested-forms.md) | Grouping fields into sub-forms and how they register with the root form |
| 5 | [Dynamic lists](dynamic-lists.md) | Lists of sub-forms, add/remove, `dispose()`, lists of simple values |
| 6 | [Submit and reset](submit-and-reset.md) | `validate()`, `submit()`, `onSubmit()`, `reset()`, async submit, loading state |
| 7 | [Recipes](recipes.md) | Reusable field composables, cross-field validation, server errors, conditional fields, surviving configuration changes |
| 8 | [API reference](api-reference.md) | Every public class, property and function |
| 9 | [Limitations and gotchas](limitations.md) | Things that will surprise you, and the workarounds |

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("<group>:compose-form:<version>")

    // pick the validator you want to use (optional, the core is validator-free)
    implementation("io.konform:konform:<version>")                // for KonformFormDefinition
    implementation("<kanyon-group>:kanyon:<version>")             // for KanyonFormDefinition
}
```

> Replace the coordinates above with your published artifacts. The code samples in these docs use the package `com.devlotfi.testlol.composeform`; adjust the imports to match your final package name.

## At a glance

```text
FormDefinition<S>              <- abstract base: holds fields, validate/submit/reset
 ├─ KonformFormDefinition      <- S = io.konform.validation.Validation<*>
 ├─ KanyonFormDefinition       <- S = kanyon FieldSchema
 └─ YourOwnFormDefinition      <- S = anything you like

FormFieldData<T, S>            <- one field: value, error, schema, blur(), validate(), reset()
Modifier.onBlur { ... }        <- fires when a text field loses focus
```
