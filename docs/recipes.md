# 7. Recipes

Practical patterns built on the core API.

## A reusable text field

Binding a field takes the same five lines every time. Wrap them once:

```kotlin
@Composable
fun FormTextField(
    field: FormFieldData<String, *>,
    modifier: Modifier = Modifier,
    label: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth().onBlur { field.blur() },
        value = field.value,
        onValueChange = { field.value = it },
        label = label?.let { { Text(it) } },
        isError = field.error != null,
        supportingText = field.error?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        singleLine = true
    )
}
```

`FormFieldData<String, *>` uses a star projection for the schema type, so the same composable works with Konform, Kanyon, or any custom adapter.

Usage:

```kotlin
FormTextField(form.email, label = "Email",
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
FormTextField(form.password, label = "Password",
    visualTransformation = PasswordVisualTransformation())
```

A matching checkbox:

```kotlin
@Composable
fun FormCheckbox(field: FormFieldData<Boolean, *>, label: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = field.value, onCheckedChange = { field.value = it })
            Text(label)
        }
        field.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

## Cross-field validation

Schemas only see their own field's value, so rules that compare two fields need a small trick.

### With Konform: read the other field inside the rule

The rule lambda runs at validation time, so it can read the other field's *current* value:

```kotlin
class RegisterForm : KonformFormDefinition() {
    val password by formField("") {
        Validation<String> { minLength(8) hint "At least 8 characters" }
    }

    val confirmPassword by formField("") {
        Validation<String> {
            constrain("Passwords do not match") { it == password.value }
        }
    }
}
```

The confirm field is only re-validated when *it* changes. Re-validate it when the password changes too:

```kotlin
OutlinedTextField(
    value = form.password.value,
    onValueChange = {
        form.password.value = it
        if (form.confirmPassword.value.isNotEmpty()) form.confirmPassword.validate()
    },
    // ...
)
```

### With any validator (including Kanyon): check at submit time

Because `validate()` and `submit()` are not overridable, add your own entry point:

```kotlin
class RegisterForm : KanyonFormDefinition() {
    val password by formField("") {
        FieldSchema().string().min(8, "At least 8 characters").build()
    }
    val confirmPassword by formField("") {
        FieldSchema().string().notBlank("Please confirm your password").build()
    }

    fun trySubmit() {
        val fieldsValid = validate()
        val matches = password.value == confirmPassword.value
        if (!matches) confirmPassword.error = "Passwords do not match"
        if (fieldsValid && matches) onSubmit()
    }
}
```

Call `form.trySubmit()` from the button instead of `form.submit()`.

(Kanyon also offers custom validation handlers and `ValidatedObject` for cross-field rules; see <https://devlotfi.github.io/kanyon/custom-validation.md>.)

## Server-side errors

Assign errors straight onto fields after the server responds:

```kotlin
fun onServerError(response: ApiError) {
    response.fieldErrors["email"]?.let { form.email.error = it }
    response.fieldErrors["username"]?.let { form.username.error = it }
}
```

The error stays until that field is validated again. With `validateOnChange = true` it disappears as soon as the user edits the field, which is usually what you want. Do not call `form.validate()` after setting server errors, because it would overwrite them.

## Conditional fields

Show/hide a field and enable/disable its schema together:

```kotlin
class ProfileForm : KonformFormDefinition() {
    val isCompany by formField(false)
    val companyName by formField("")

    fun setCompany(value: Boolean) {
        isCompany.value = value
        companyName.schema = if (value) Validation<String> { notBlank() hint "Required" } else null
        companyName.error = null
    }
}

if (form.isCompany.value) {
    FormTextField(form.companyName, label = "Company name")
}
```

Hidden fields stay registered, so setting `schema = null` is what keeps them from blocking submit. For whole groups, use a sub-form and `dispose()` it:

```kotlin
var company: CompanyForm? by mutableStateOf(null)

fun enableCompany() { company = CompanyForm(this) }
fun disableCompany() { company?.dispose(); company = null }
```

## Edit screens: pre-filling from existing data

Pass data to the form's constructor and use it as `initialValue`. `reset()` then returns to the loaded values:

```kotlin
class EditProfileForm(user: User, private val onSave: (User) -> Unit) : KonformFormDefinition() {
    val name by formField(user.name) { Validation<String> { notBlank() } }
    val bio by formField(user.bio)

    override fun onSubmit() = onSave(User(name.value, bio.value))
}

val form = remember(user.id) { EditProfileForm(user) { viewModel.save(it) } }
```

Keying `remember` on `user.id` creates a fresh form if a different user is opened.

## Keeping the form alive

| Need | Use |
|---|---|
| Survive recomposition only | `remember { MyForm() }` |
| Survive rotation / configuration change | Create the form in a `ViewModel` and expose it |
| Survive navigation (back stack), keep what the user typed | Keep the instance in a ViewModel scoped to the navigation entry or in a store that outlives the screen |
| Survive process death | Persist the raw values yourself (e.g. `SavedStateHandle`) and re-create the form with them |

`rememberSaveable` cannot store a form directly because it is not a `Parcelable`/`Saver`-friendly type. Save the *values* instead.

If you use a small keyed store like the one in the playground project, the pattern is simply "get or create the form for this screen":

```kotlin
val form by viewModel.dataStore.rememberValueInDataStore("HomeScreen") { MainForm() }
```

## Testing forms without Compose

Forms are plain Kotlin classes, and `value`/`error` are ordinary snapshot state, so they can be unit-tested directly:

```kotlin
class LoginFormTest {
    @Test
    fun `empty email is invalid`() {
        val form = LoginForm()
        form.email.value = ""
        assertEquals("Email is required", form.email.error)
        assertFalse(form.validate())
    }

    @Test
    fun `valid form submits`() {
        var submitted = false
        val form = LoginForm { _, _ -> submitted = true }
        form.email.value = "a@b.co"
        form.password.value = "longenough"
        form.submit()
        assertTrue(submitted)
    }
}
```

## Showing a summary of all errors

`FormFieldData` instances are stored in `fields`, but that set is not observable. Collect the errors you care about explicitly:

```kotlin
val errors get() = listOfNotNull(firstName.error, lastName.error, email.error)

if (form.errors.isNotEmpty()) {
    Text("Please fix ${form.errors.size} error(s)")
}
```

Because `error` is Compose state, this getter recomposes correctly.

## Scrolling to the first error

```kotlin
val scroll = rememberScrollState()
val scope = rememberCoroutineScope()

Button(onClick = {
    if (!form.validate()) scope.launch { scroll.animateScrollTo(0) }
    else form.onSubmit()
}) { Text("Save") }
```

For per-field scrolling, use `BringIntoViewRequester` on each field and call `bringIntoView()` for the first one with a non-null `error`.

## Full example: a profile form with a contact list

```kotlin
// ---- forms ----
class ContactForm(parent: KonformFormDefinition) : KonformFormDefinition(parent = parent) {
    val name by formField("") {
        Validation<String> { notBlank() hint "Required"; minLength(5) hint "At least 5 characters" }
    }
    val email by formField("") {
        Validation<String> { notBlank() hint "Required" }
    }
}

class MainForm : KonformFormDefinition() {
    init { validateOnChange = true; validateOnBlur = true }

    val firstName by formField("") {
        Validation<String> { notBlank() hint "Required"; minLength(5) hint "At least 5 characters" }
    }
    val lastName by formField("") {
        Validation<String> { notBlank() hint "Required"; minLength(5) hint "At least 5 characters" }
    }

    val contacts = mutableStateListOf(ContactForm(this))
    fun addContact() { contacts.add(ContactForm(this)) }
    fun removeContact(c: ContactForm) { contacts.remove(c); c.dispose() }

    override fun onSubmit() { println("Valid form") }
}

// ---- screen ----
@Composable
fun HomeScreen(form: MainForm) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        FormTextField(form.firstName, label = "First name")
        FormTextField(form.lastName, label = "Last name")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { form.addContact() }) { Text("Add") }
            Button(onClick = { form.contacts.removeLastOrNull()?.dispose() }) { Text("Remove") }
        }

        form.contacts.forEach { contact ->
            key(contact) {
                FormTextField(contact.name, label = "Contact name")
                FormTextField(contact.email, label = "Contact email")
            }
        }

        Button(onClick = { form.submit() }) { Text("Submit") }
    }
}
```
