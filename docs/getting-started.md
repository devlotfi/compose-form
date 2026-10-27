# 1. Getting started

This guide builds a complete login form from scratch. Examples use **Konform**; every example works identically
with Kanyon or your own validator (see [Setting up a validator](validators.md)).

## Core concepts

| Concept | Class | Role |
|---------|-------|------|
| Form | `FormDefinition<S>` | Holds the fields, knows how to validate, submit and reset them |
| Field | `FormFieldData<T, S>` | Holds `value: T`, `error: String?`, `touched: Boolean` and an optional schema `S` |
| Schema | `S` (your choice) | Whatever your validation library uses to describe rules for one field |
| Validator bridge | `validateField(value, schema)` | The **only** place where the library touches your validation library |

`S` is a type parameter of the form. A Konform form uses `S = Validation<*>`, a Kanyon form uses `S = FieldSchema`.

## Step 1: Choose a base class

Because the library is validation-agnostic, you first need a base class that bridges to your validator.
Copy one of the ready-made ones from [Setting up a validator](validators.md):

```kotlin
abstract class KonformFormDefinition : FormDefinition<Validation<*>>() {
    @Suppress("UNCHECKED_CAST")
    override fun <T> validateField(value: T, schema: Validation<*>?): String? {
        val result = (schema as? Validation<T>)?.validate(value)
        val error = result?.errors?.firstOrNull()
        return if (error != null) error.toString() else null
    }
}
```

You write this **once per project**, not once per form.

## Step 2: Define your form

```kotlin
import com.github.devlotfi.composeform.FormField
import io.konform.validation.Validation
import io.konform.validation.constraints.*

class LoginForm(
    private val onValid: (email: String, password: String) -> Unit
) : KonformFormDefinition() {

    val email by formField("") {
        Validation<String> {
            minLength(1) hint "Email is required"
            pattern(".+@.+\\..+") hint "Invalid email address"
        }
    }

    val password by formField("") {
        Validation<String> {
            minLength(8) hint "Password must be at least 8 characters"
        }
    }

    val rememberMe by formField(false)   // no schema: always valid

    override fun onSubmit() {
        onValid(email.value, password.value)
    }
}
```

What happened here:

- `formField(initialValue) { schema }` is a **property delegate provider**. When the form is constructed it creates a
  `FormFieldData`, registers it in `fields` under the **property name** (`"email"`, `"password"`, ...) and returns the
  delegate.
- The trailing lambda builds the schema. It is optional: a field without a schema never has an error.
- `onSubmit()` is abstract. It is called by `submit()` only when every field is valid.
- Reading `email` gives you a `FormFieldData<String, Validation<*>>`, **not** a `String`. Use `email.value`.

## Step 3: Use the form in Compose

```kotlin
@Composable
fun LoginScreen(onLoggedIn: (String, String) -> Unit) {
    val form = remember { LoginForm(onLoggedIn) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        OutlinedTextField(
            value = form.email.value,
            onValueChange = { form.email.value = it },
            label = { Text("Email") },
            isError = form.email.touched && form.email.error != null,
            supportingText = {
                if (form.email.touched) form.email.error?.let { Text(it) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onBlur { form.email.blur() }
        )

        OutlinedTextField(
            value = form.password.value,
            onValueChange = { form.password.value = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = form.password.touched && form.password.error != null,
            supportingText = {
                if (form.password.touched) form.password.error?.let { Text(it) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onBlur { form.password.blur() }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = form.rememberMe.value,
                onCheckedChange = { form.rememberMe.value = it }
            )
            Text("Remember me")
        }

        Button(onClick = { form.submit() }, modifier = Modifier.fillMaxWidth()) {
            Text("Log in")
        }
    }
}
```

Key points:

1. **Writing** `field.value = x` marks the field as touched and (by default) validates it immediately.
2. **Showing errors**: always gate on `touched`. Every field is validated once when the form is created, so `error`
   is already non-null for an empty required field, but you do not want to show it before the user interacts.
3. **`Modifier.onBlur`** fires when a field that was focused loses focus. It calls `blur()` which marks the field as
   touched and validates it (if `validateOnBlur` is on).
4. **`submit()`** validates all fields, marks them all as touched (so every error becomes visible), and only calls
   `onSubmit()` when there are no errors.

## Step 4: Reset

```kotlin
TextButton(onClick = { form.reset() }) { Text("Clear") }
```

`reset()` restores every field to its `initialValue`, clears errors and sets `touched = false`.

## Remembering the form

| Where | How |
|-------|-----|
| Survives recomposition only | `val form = remember { LoginForm(...) }` |
| Survives configuration change (Android) | Create it inside a `ViewModel`, or use `rememberSaveable` with a custom saver for the *values* |
| Survives navigation back stack | Keep it in a `ViewModel` scoped to the nav entry |

See [Compose UI patterns](compose-ui-patterns.md#using-a-viewmodel) for a ViewModel example.

## Next steps

- [API reference](api-reference.md)
- [Validator setup (Konform, Kanyon, custom)](validators.md)
- [Nested fields](nested-fields.md) and [lists](lists.md)
