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

- [Docs](https://github.com/devlotfi/compose-form/blob/main/docs/intro.md)
- [llms.txt](https://devlotfi.github.io/compose-form/llms.txt)

## Quick start

### 1. Define a form

A form is a class. Each field is a property delegate that holds its own `value`, `error` and `schema`.

This first example uses no validation library at all. Compose Form only needs you to answer one question: *given a value and a schema, what is the error message?*

```kotlin
class LoginForm(private val onValid: (String) -> Unit) : FormDefinition<Unit>() {
    val email by formField("")
 
    override fun <T> validateField(value: T, schema: Unit?): String? =
        if (value is String && value.isBlank()) "Email is required" else null
 
    override fun onSubmit() = onValid(email.value)
}
```

### 2. Bind it to Compose

Remember the form, bind its field state to a text field, and submit. `onSubmit` only runs when every field is valid.

```kotlin
val form = remember { LoginForm { email -> /* handle valid email */ } }
 
OutlinedTextField(
    value = form.email.value,
    onValueChange = { form.email.value = it },
    isError = form.email.error != null,
    supportingText = form.email.error?.let { { Text(it) } },
    modifier = Modifier.onBlur { form.email.blur() }
)
Button(onClick = { form.submit() }) {
    Text("Continue")
}
```

Every field binds the same way:

| Compose parameter | Bind to |
|---|---|
| `value` | `field.value` |
| `onValueChange` | `{ field.value = it }` |
| `isError` / `supportingText` | `field.error` |
| `Modifier.onBlur { }` | `field.blur()` |

### 3. Plug in a validation library

Per-field rules are much nicer with a real validator. A schema is whatever your validation library produces, and the adapter below turns it into an error message.

<details>
<summary>Konform adapter (copy once, reuse for every form)</summary>
```kotlin
abstract class KonformFormDefinition(parent: FormDefinition<Validation<*>>? = null) :
    FormDefinition<Validation<*>>(root = parent) {

    @Suppress("UNCHECKED_CAST")
    override fun <T> validateField(value: T, schema: Validation<*>?): String? {
        val result = (schema as? Validation<T>)?.validate(value)
        return result?.errors?.firstOrNull()?.message
    }
}
```
 
</details>
<details>
<summary>Kanyon adapter</summary>
```kotlin
abstract class KanyonFormDefinition(
    parent: FormDefinition<FieldSchema>? = null,
    validateOnChange: Boolean = true,
    validateOnBlur: Boolean = true
) : FormDefinition<FieldSchema>(
    validateOnChange = validateOnChange,
    validateOnBlur = validateOnBlur,
    root = parent
) {
    override fun <T> validateField(value: T, schema: FieldSchema?): String? =
        schema?.validate(value)
}
```

</details>
Then declare each field's schema right next to it. Pick your library:

**Konform**

```kotlin
class SignUpForm(private val onValid: (String, String) -> Unit) : KonformFormDefinition() {
    init {
        validateOnChange = false   // don't nag while typing
        validateOnBlur = true      // validate when the user leaves the field
    }
 
    val email by formField("") {
        Validation<String> {
            notBlank() hint "Email is required"
            pattern("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") hint "Invalid email"
        }
    }
 
    val password by formField("") {
        Validation<String> {
            minLength(8) hint "At least 8 characters"
        }
    }
 
    override fun onSubmit() = onValid(email.value, password.value)
}
```

**Kanyon**

```kotlin
class SignUpForm(private val onValid: (String, String) -> Unit) :
    KanyonFormDefinition(validateOnChange = false) {
 
    val email by formField("") {
        FieldSchema()
            .string()
            .notBlank("Email is required")
            .email("Invalid email")
            .build()
    }
 
    val password by formField("") {
        FieldSchema()
            .string()
            .min(8, "At least 8 characters")
            .build()
    }
 
    override fun onSubmit() = onValid(email.value, password.value)
}
```

### 4. Wrap the binding once

Bind-and-forget with a reusable composable that works with any validator:

```kotlin
@Composable
fun FormTextField(
    field: FormFieldData<String, *>,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth().onBlur { field.blur() },
        value = field.value,
        onValueChange = { field.value = it },
        label = { Text(label) },
        isError = field.error != null,
        supportingText = field.error?.let { { Text(it) } },
        singleLine = true
    )
}
 
@Composable
fun SignUpScreen(viewModel: SignUpViewModel = viewModel()) {
    val form = viewModel.form   // created in the ViewModel so it survives rotation
 
    Column(Modifier.padding(16.dp)) {
        FormTextField(form.email, label = "Email")
        FormTextField(form.password, label = "Password")
        Button(onClick = { form.submit() }) { Text("Create account") }
    }
}
```

### 5. Group fields with nested forms

Sub-forms take their parent so the root form can validate, submit and reset everything at once.

```kotlin
class AddressForm(parent: KonformFormDefinition) : KonformFormDefinition(parent = parent) {
    val street by formField("") { Validation<String> { notBlank() hint "Street is required" } }
    val city by formField("") { Validation<String> { notBlank() hint "City is required" } }
}
 
class CheckoutForm : KonformFormDefinition() {
    val fullName by formField("") { Validation<String> { notBlank() hint "Name is required" } }
    val address = AddressForm(this)    // pass the root form
}
 
FormTextField(form.fullName, label = "Full name")
FormTextField(form.address.street, label = "Street")
FormTextField(form.address.city, label = "City")
Button(onClick = { form.submit() }) { Text("Pay") }   // validates all three fields
```

### 6. Build dynamic lists

Use a `mutableStateListOf` of sub-forms. Always `dispose()` an item you remove so its fields stop being validated.

```kotlin
class ContactForm(parent: KonformFormDefinition) : KonformFormDefinition(parent = parent) {
    val name by formField("") { Validation<String> { notBlank() hint "Name is required" } }
    val email by formField("") { Validation<String> { notBlank() hint "Email is required" } }
}
 
class TeamForm : KonformFormDefinition() {
    val contacts = mutableStateListOf(ContactForm(this))
 
    fun addContact() { contacts.add(ContactForm(this)) }
 
    fun removeContact(contact: ContactForm) {
        contacts.remove(contact)
        contact.dispose()               // unregister its fields from the root
    }
}
 
form.contacts.forEach { contact ->
    key(contact) {
        FormTextField(contact.name, label = "Name")
        FormTextField(contact.email, label = "Email")
        TextButton(onClick = { form.removeContact(contact) }) { Text("Remove") }
    }
}
Button(onClick = { form.addContact() }) { Text("Add contact") }
```

### 7. Handle server errors

Errors are plain state, so you can set them yourself after a request:

```kotlin
form.email.error = "This email is already registered"
```

That's it. For validation timing, checkboxes, dropdowns, conditional fields, cross-field rules, resetting, testing and more, see the [docs](https://github.com/devlotfi/compose-form/blob/main/docs/intro.md).