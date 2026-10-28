# 1. Getting started

## Core concepts

compose-form has only three building blocks.

### `FormDefinition<S>`

An abstract class you extend. **Each subclass is one form** (or one group of fields inside a bigger form). It owns:

- the set of fields registered to it (`fields`),
- the validation timing flags (`validateOnChange`, `validateOnBlur`),
- the operations `validate()`, `submit()`, `reset()` and `dispose()`,
- the hook `onSubmit()` that you override.

`S` is the **schema type** of your validation library. compose-form never looks inside a schema; it only hands it back to you through `validateField` (see [Validation setup](validation-setup.md)).

### `FormFieldData<T, S>`

The state of one field:

| Member | Type | Description |
|---|---|---|
| `value` | `T` | Current value. **Setting it may trigger validation** (see `validateOnChange`). |
| `error` | `String?` | Current error message, `null` when valid. Compose state. |
| `schema` | `S?` | The schema used to validate this field. Compose state, can be replaced at runtime. |
| `initialValue` | `T` | The value the field was created with. Used by `reset()`. |
| `blur()` | | Call when the field loses focus. Validates if `validateOnBlur` is on. |
| `validate()` | | Validate now, regardless of the flags. |
| `reset()` | | Restore `initialValue` and clear the error. |

### `formField(...)` delegate

Declares a field inside a `FormDefinition`:

```kotlin
val email by formField("") {            // initial value, then the schema block
    Validation<String> { notBlank() }
}
```

The delegate creates the `FormFieldData`, registers it on the form (and on the root form, if any) and returns it. Because the property type is inferred from the initial value, `formField("")` is a `FormFieldData<String, S>` and `formField(0)` is a `FormFieldData<Int, S>`.

## Your first form

### 1. Pick a validator and create a base form

For Konform you only need the adapter that ships with compose-form:

```kotlin
class SignUpForm : KonformFormDefinition() {
    val username by formField("") {
        Validation<String> {
            notBlank()
            minLength(3)
        }
    }

    val email by formField("") {
        Validation<String> {
            notBlank()
            pattern("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") hint "Invalid email"
        }
    }

    override fun onSubmit() {
        // Only called when every field is valid
        println("Creating ${username.value} <${email.value}>")
    }
}
```

> Using Kanyon instead? Extend `KanyonFormDefinition` and write the schema with `FieldSchema()` (see [Validation setup](validation-setup.md#kanyon)).

### 2. Configure validation timing (optional)

Both flags default to `true`. Change them in an `init` block:

```kotlin
class SignUpForm : KonformFormDefinition() {
    init {
        validateOnChange = false  // do not validate while typing
        validateOnBlur = true     // validate when the user leaves the field
    }
    // fields...
}
```

| `validateOnChange` | `validateOnBlur` | Behaviour |
|---|---|---|
| `true` | `true` | Errors appear while typing and when leaving the field. |
| `false` | `true` | Errors appear when leaving the field (a common, friendly default for UX). |
| `true` | `false` | Errors appear only while typing. |
| `false` | `false` | Errors appear only after `validate()` / `submit()`. |

### 3. Hold the form instance

The form is a normal object. Keep it somewhere that survives recomposition:

```kotlin
// Simple screens
val form = remember { SignUpForm() }

// Screens that must survive configuration changes (rotation, process-kept state)
class SignUpViewModel : ViewModel() {
    val form = SignUpForm()
}
val form = viewModel<SignUpViewModel>().form
```

See [Recipes](recipes.md#keeping-the-form-alive) for more options.

### 4. Bind fields to composables

```kotlin
@Composable
fun SignUpScreen(viewModel: SignUpViewModel = viewModel()) {
    val form = viewModel.form

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().onBlur { form.username.blur() },
            value = form.username.value,
            onValueChange = { form.username.value = it },
            label = { Text("Username") },
            isError = form.username.error != null,
            supportingText = form.username.error?.let { { Text(it) } }
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().onBlur { form.email.blur() },
            value = form.email.value,
            onValueChange = { form.email.value = it },
            label = { Text("Email") },
            isError = form.email.error != null,
            supportingText = form.email.error?.let { { Text(it) } }
        )

        Button(onClick = { form.submit() }) { Text("Create account") }
    }
}
```

The pattern is always the same four lines:

| Compose parameter | Bind to |
|---|---|
| `value` | `field.value` |
| `onValueChange` | `{ field.value = it }` |
| `isError` / `supportingText` | `field.error` |
| `Modifier.onBlur { }` | `field.blur()` |

Because `value` and `error` are backed by Compose state, reading them inside a composable subscribes that composable to changes. Nothing else is required.

## The `onBlur` modifier

```kotlin
fun Modifier.onBlur(onBlur: () -> Unit): Modifier
```

Compose has no built-in "lost focus" callback. `onBlur` remembers whether the element was focused and calls your lambda on the **focused → unfocused** transition only. It does not fire on first composition or when the field is focused.

```kotlin
OutlinedTextField(
    modifier = Modifier.onBlur { form.email.blur() },
    // ...
)
```

Apply it to the focusable element (`TextField`, `OutlinedTextField`, `BasicTextField`, or a custom `focusable()` component). Without it, `validateOnBlur` has no effect, because `blur()` is never called.

## Next

- [Validation setup](validation-setup.md) to connect a validator
- [Fields and state](fields.md) for every field type and the full field API
