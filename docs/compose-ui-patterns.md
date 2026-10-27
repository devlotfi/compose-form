# 7. Compose UI patterns

The library is UI-agnostic: it exposes `value`, `error` and `touched`, and you bind them to any composable.
This page shows reusable components and integration patterns (Material 3 is used in the examples).

## Reusable field components

Writing `isError`, `supportingText` and `onBlur` for every input gets repetitive. Wrap it once.

### Text field

```kotlin
@Composable
fun <S> FormTextField(
    field: FormFieldData<String, S>,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true
) {
    val error = if (field.touched) field.error else null

    OutlinedTextField(
        value = field.value,
        onValueChange = { field.value = it },
        label = { Text(label) },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .onBlur { field.blur() }
    )
}
```

### Number field (`Int`)

A text field edits a `String`; keep the typed text separate from the parsed value when the field is numeric.

```kotlin
@Composable
fun <S> FormIntField(field: FormFieldData<Int, S>, label: String, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(field.value.toString()) }
    val error = if (field.touched) field.error else null

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toIntOrNull()?.let { field.value = it }
        },
        label = { Text(label) },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth().onBlur { field.blur() }
    )
}
```

> If an input such as `"abc"` should be reported as an error, make the field a `String` and validate with
> a pattern (or convert in `onSubmit()`), rather than silently ignoring unparsable input.

### Checkbox / switch

```kotlin
@Composable
fun <S> FormCheckbox(field: FormFieldData<Boolean, S>, label: String) {
    val error = if (field.touched) field.error else null
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = field.value, onCheckedChange = { field.value = it })
            Text(label)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
```

### Dropdown (enum or list of options)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T, S> FormDropdown(
    field: FormFieldData<T?, S>,
    label: String,
    options: List<T>,
    optionLabel: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }
    val error = if (field.touched) field.error else null

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = field.value?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false; field.blur() }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { field.value = option; expanded = false }
                )
            }
        }
    }
}
```

Usage with a nullable field:

```kotlin
enum class Role { Admin, Editor, Viewer }

class InviteForm : KonformFormDefinition() {
    val role by formField<Role?>(null) {
        Validation<Role?> { constrain("Choose a role") { it != null } }
    }
    override fun onSubmit() {}
}

FormDropdown(form.role, "Role", Role.entries) { it.name }
```

### Radio group / chips / any custom control

Any control follows the same recipe: read `field.value`, assign `field.value = x` on change, call `field.blur()` when
interaction ends (optional), render `field.error` when `field.touched`.

```kotlin
Role.entries.forEach { role ->
    Row(Modifier.selectable(form.role.value == role, onClick = { form.role.value = role })) {
        RadioButton(selected = form.role.value == role, onClick = null)
        Text(role.name)
    }
}
```

## Submit button

### Always enabled (errors appear on click)

```kotlin
Button(onClick = { form.submit() }) { Text("Save") }
```

This is the friendliest UX: after a failed click, every field is touched so all errors show.

### Disabled until valid

```kotlin
val isValid = form.fields.values.none { it.error != null }   // reads Compose state: recomposes automatically
Button(onClick = { form.submit() }, enabled = isValid) { Text("Save") }
```

Make sure your validation mode produces errors early (`validateOnChange = true`), otherwise `isValid` stays `true`
until the first validation.

## Using a ViewModel

`FormDefinition` is a plain class using Compose snapshot state, so it can live in a `ViewModel` and survive configuration
changes.

```kotlin
class LoginViewModel(private val repo: AuthRepository) : ViewModel() {

    val form = object : KonformFormDefinition() {
        val email by formField("") {
            Validation<String> { pattern(".+@.+\\..+") hint "Invalid email" }
        }
        val password by formField("") { Validation<String> { minLength(8) hint "Min 8 characters" } }

        override fun onSubmit() = this@LoginViewModel.login(email.value, password.value)
    }
}
```

Anonymous objects hide their members from outside code, so a named class is usually better:

```kotlin
class LoginForm(private val onValid: (String, String) -> Unit) : KonformFormDefinition() {
    val email by formField("") { Validation<String> { pattern(".+@.+\\..+") hint "Invalid email" } }
    val password by formField("") { Validation<String> { minLength(8) hint "Min 8 characters" } }
    override fun onSubmit() = onValid(email.value, password.value)
}

class LoginViewModel(private val repo: AuthRepository) : ViewModel() {
    val form = LoginForm { email, password -> login(email, password) }

    var isSubmitting by mutableStateOf(false)
        private set

    private fun login(email: String, password: String) {
        viewModelScope.launch {
            isSubmitting = true
            when (val result = repo.login(email, password)) {
                is Result.Success -> { /* navigate */ }
                is Result.Failure -> form.email.error = result.message
            }
            isSubmitting = false
        }
    }
}

@Composable
fun LoginRoute(viewModel: LoginViewModel = viewModel()) {
    LoginScreen(viewModel.form, viewModel.isSubmitting)
}
```

## Async submit and loading state

`onSubmit()` is synchronous. Launch coroutines from it and track loading state yourself:

```kotlin
@Composable
fun LoginScreen(form: LoginForm, isSubmitting: Boolean) {
    Column(Modifier.padding(16.dp)) {
        FormTextField(form.email, "Email")
        FormTextField(form.password, "Password", visualTransformation = PasswordVisualTransformation())

        Button(onClick = { form.submit() }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
            if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Log in")
        }
    }
}
```

Without a ViewModel (state hoisted into the composable):

```kotlin
@Composable
fun LoginScreen(api: AuthApi) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }

    val form = remember {
        LoginForm { email, password ->
            scope.launch {
                loading = true
                runCatching { api.login(email, password) }
                loading = false
            }
        }
    }
    // ...
}
```

## Focus handling

Move to the next field with the keyboard and submit from the last one:

```kotlin
val focusManager = LocalFocusManager.current

OutlinedTextField(
    value = form.email.value,
    onValueChange = { form.email.value = it },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
    modifier = Modifier.onBlur { form.email.blur() }
)

OutlinedTextField(
    value = form.password.value,
    onValueChange = { form.password.value = it },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); form.submit() }),
    modifier = Modifier.onBlur { form.password.blur() }
)
```

## Scroll to the first error

`validate()` returns a map in field declaration order, so the first key is the first invalid field:

```kotlin
val firstInvalid: String? = form.validate().keys.firstOrNull()
if (firstInvalid != null) {
    scope.launch { listState.animateScrollToItem(indexOfField(firstInvalid)) }
}
```

Combine it with `BringIntoViewRequester` or a `Map<String, Int>` of field names to item indexes in a `LazyColumn`.

## Previews and tests

Because a form is just a class, previews are trivial:

```kotlin
@Preview
@Composable
fun LoginPreview() {
    val form = remember { LoginForm { _, _ -> }.apply { email.value = "bad"; password.blur() } }
    LoginScreen(form, isSubmitting = false)
}
```
