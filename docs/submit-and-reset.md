# 6. Submit and reset

## `validate()`

```kotlin
fun validate(): Boolean
```

Validates **every field registered on the form** (including fields of sub-forms that registered on it), sets each field's `error`, and returns `true` only if all are valid. It does **not** call `onSubmit()`.

```kotlin
if (form.validate()) { /* every field is valid */ }
```

Use it for step-by-step wizards, enabling an action only when valid, or custom submit flows.

> `validate()` always runs all fields (it does not stop at the first failure), so all errors show at once.

## `submit()` and `onSubmit()`

```kotlin
fun submit()          // validate(), then onSubmit() if valid
open fun onSubmit()   // override this
```

```kotlin
class LoginForm : KonformFormDefinition() {
    val email by formField("") { Validation<String> { notBlank() } }
    val password by formField("") { Validation<String> { minLength(8) } }

    override fun onSubmit() {
        // Runs only when all fields are valid
        println("Submitting ${email.value}")
    }
}

Button(onClick = { form.submit() }) { Text("Log in") }
```

If any field is invalid, errors appear and `onSubmit()` is not called.

### Passing a callback instead of overriding

Often the form should not know what "submit" does (navigation, repository calls). Take a callback:

```kotlin
class LoginForm(private val onValid: (email: String, password: String) -> Unit) :
    KonformFormDefinition() {

    val email by formField("") { Validation<String> { notBlank() } }
    val password by formField("") { Validation<String> { minLength(8) } }

    override fun onSubmit() = onValid(email.value, password.value)
}

val form = remember { LoginForm { email, password -> viewModel.login(email, password) } }
```

### Async submit and loading state

`onSubmit()` is synchronous and the form has no coroutine scope. Do the async work in your ViewModel and track loading yourself:

```kotlin
class SignUpViewModel : ViewModel() {
    var isSubmitting by mutableStateOf(false)
        private set

    val form = SignUpForm(onValid = { username, email -> register(username, email) })

    private fun register(username: String, email: String) {
        viewModelScope.launch {
            isSubmitting = true
            try {
                api.register(username, email)
            } catch (e: ApiException) {
                // map server errors back to fields, see Recipes
                form.email.error = e.message
            } finally {
                isSubmitting = false
            }
        }
    }
}

Button(
    enabled = !viewModel.isSubmitting,
    onClick = { viewModel.form.submit() }
) { Text(if (viewModel.isSubmitting) "Saving…" else "Create account") }
```

### Submit on keyboard "Done"

```kotlin
OutlinedTextField(
    // ...
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = { form.submit() })
)
```

## `reset()`

```kotlin
fun reset()
```

Calls `reset()` on every registered field: the value goes back to `initialValue` and the error is cleared.

```kotlin
Button(onClick = { form.reset() }) { Text("Clear") }
```

Details worth knowing:

- Field `reset()` sets the value first and then clears the error. If `validateOnChange` is on, the value assignment triggers validation, but the error is cleared right after, so the form comes back clean.
- `reset()` on a form restores **fields only**. It does not remove or re-create items in a [dynamic list](dynamic-lists.md).

### Resetting a form with a dynamic list

Add a function that restores the list too:

```kotlin
class MainForm : KonformFormDefinition() {
    val contacts = mutableStateListOf(ContactForm(this))

    fun resetForm() {
        while (contacts.size > 1) {
            contacts.removeAt(contacts.lastIndex).dispose()
        }
        reset()
    }
}
```

### Reset after a successful submit

```kotlin
override fun onSubmit() {
    save(email.value)
    reset()
}
```

### Resetting a single field

```kotlin
form.email.reset()
```

## `dispose()`

```kotlin
fun dispose()
```

Unregisters the form's fields from its root. It is meant for sub-forms that are discarded (list items). Calling it on a top-level form does nothing, since there is no root. See [Dynamic lists](dynamic-lists.md#why-dispose-is-required).

## Checking validity without showing errors

`validate()` always writes errors into the fields, and there is currently no side-effect-free "is this valid?" check. If you need to enable/disable a button based on validity, either keep the button enabled and show errors on press (the intended flow), or see the suggestions in [Limitations](limitations.md).

## Next

- [Recipes](recipes.md)
