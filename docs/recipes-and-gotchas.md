# 8. Recipes, gotchas & FAQ

## Recipe 1: Registration form (cross-field, checkbox, async errors)

```kotlin
class RegistrationForm(
    private val onValid: (RegistrationData) -> Unit
) : KonformFormDefinition() {

    val username by formField("") {
        Validation<String> {
            minLength(3) hint "At least 3 characters"
            pattern("^[a-zA-Z0-9_]+$") hint "Letters, digits and underscores only"
        }
    }

    val email by formField("") {
        Validation<String> { pattern(".+@.+\\..+") hint "Invalid email address" }
    }

    // password MUST be declared before confirmPassword (confirmPassword reads it)
    val password by formField("") {
        Validation<String> {
            minLength(8) hint "At least 8 characters"
            pattern(".*[0-9].*") hint "Must contain a digit"
        }
    }

    val confirmPassword by formField("") {
        Validation<String> { constrain("Passwords do not match") { it == password.value } }
    }

    val acceptTerms by formField(false) {
        Validation<Boolean> { constrain("You must accept the terms") { it } }
    }

    fun setPassword(value: String) {
        password.value = value
        if (confirmPassword.touched) confirmPassword.validate()
    }

    fun showEmailTaken() {
        email.error = "This email is already registered"
        email.touched = true
    }

    override fun onSubmit() = onValid(
        RegistrationData(username.value, email.value, password.value)
    )
}

data class RegistrationData(val username: String, val email: String, val password: String)
```

```kotlin
@Composable
fun RegistrationScreen(viewModel: RegistrationViewModel = viewModel()) {
    val form = viewModel.form

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormTextField(form.username, "Username")
        FormTextField(form.email, "Email", keyboardType = KeyboardType.Email)

        // password uses the form's helper so confirmPassword stays in sync
        val pwError = if (form.password.touched) form.password.error else null
        OutlinedTextField(
            value = form.password.value,
            onValueChange = form::setPassword,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = pwError != null,
            supportingText = { pwError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth().onBlur { form.password.blur() }
        )

        FormTextField(form.confirmPassword, "Confirm password", visualTransformation = PasswordVisualTransformation())
        FormCheckbox(form.acceptTerms, "I accept the terms and conditions")

        Button(onClick = { form.submit() }, modifier = Modifier.fillMaxWidth()) { Text("Create account") }
    }
}
```

## Recipe 2: Checkout (nested sub-form + list of sub-forms)

```kotlin
class AddressForm : KonformSubForm() {
    val street by formField("") { Validation<String> { minLength(3) hint "Street is too short" } }
    val city by formField("") { Validation<String> { minLength(1) hint "City is required" } }
    val zip by formField("") { Validation<String> { pattern("[0-9]{5}") hint "5 digits expected" } }
}

class ItemForm(initialSku: String = "", initialQty: Int = 1) : KonformSubForm() {
    val id: Long = nextRowId++
    val sku by formField(initialSku) { Validation<String> { minLength(1) hint "SKU is required" } }
    val quantity by formField(initialQty) { Validation<Int> { minimum(1) hint "At least 1" } }
}

class CheckoutForm(private val onValid: (CheckoutData) -> Unit) : KonformFormDefinition() {

    val email by formField("") { Validation<String> { pattern(".+@.+\\..+") hint "Invalid email" } }
    val shipping = AddressForm()
    val items = mutableStateListOf(ItemForm())

    fun addItem() = items.add(ItemForm())
    fun removeItem(item: ItemForm) = items.remove(item)

    fun submitAll() {
        val results = buildList {
            add(validate())
            add(shipping.validate())
            items.forEach { add(it.validate()) }
        }
        if (results.all { it.isEmpty() } && items.isNotEmpty()) onSubmit()
    }

    override fun onSubmit() = onValid(
        CheckoutData(
            email = email.value,
            address = Address(shipping.street.value, shipping.city.value, shipping.zip.value),
            items = items.map { LineItem(it.sku.value, it.quantity.value) }
        )
    )
}

data class CheckoutData(val email: String, val address: Address, val items: List<LineItem>)
```

See [Nested fields](nested-fields.md) and [Lists](lists.md) for the UI.

## Recipe 3: Search form that validates on submit only

```kotlin
class SearchForm(private val onSearch: (String) -> Unit)
    : KonformFormDefinition(validateOnChange = false, validateOnBlur = false) {

    val query by formField("") { Validation<String> { minLength(2) hint "Type at least 2 characters" } }
    override fun onSubmit() = onSearch(query.value)
}
```

(This needs the constructor parameters to be forwarded by your base class, see
[API reference](api-reference.md#constructor-parameters).)

## Recipe 4: Edit screen with "Save" enabled only when dirty and valid

```kotlin
val isDirty = form.fields.values.any { it.value != it.initialValue }
val isValid = form.fields.values.none { it.error != null }

Button(onClick = { form.submit() }, enabled = isDirty && isValid) { Text("Save changes") }
```

---

## Gotchas

These behaviours follow directly from how the library is implemented. Knowing them saves debugging time.

### 1. Every field validates once at creation

`FormFieldData` runs `validate()` in its `init` block. So `error` is non-null for an empty required field before the
user types anything. Always gate error display on `touched`.

### 2. The `error` argument of `formField` is overwritten

Because of the initial validation, `formField("", error = "Preset")` ends up with whatever the validator returns.
To show a preset or server error, assign `field.error = "..."` **after** construction.

### 3. Assigning `value` always marks the field as touched

This includes programmatic assignments (for instance pre-filling after a network call). If you do not want errors to show
immediately, reset the flag afterwards:

```kotlin
form.name.value = loaded.name
form.name.touched = false
```

Or pass the loaded values as constructor arguments, as shown in
[Pre-filling a form](validation-behaviour.md#pre-filling-a-form-edit-screens).

### 4. `reset()` re-assigns the value before clearing the error

`reset()` sets `value = initialValue` (which may validate with `validateOnChange`), then sets `error = null` and
`touched = false`. After a reset the field therefore has **no error even if the initial value is invalid** until the next
validation. `submit()` always re-validates, so this is safe.

### 5. `validate()`, `submit()`, `reset()` are final and only know about the form's own fields

Nested forms and lists are not part of `fields`. Use your own `submitAll()` / `resetAll()` that call the same methods on
the children (see [Nested fields](nested-fields.md)).

### 6. Declare dependencies before dependants

A cross-field rule reads another field lazily at validation time, but the initial validation happens at construction, in
declaration order. If `confirmPassword` is declared before `password`, accessing `password` during that initial
validation fails because the property is not registered yet.

### 7. Reading `form.email` gives a field, not a value

```kotlin
val x = form.email          // FormFieldData<String, S>
val y = form.email.value    // String
```

### 8. Replace collections and objects, do not mutate them

`field.value = field.value + item` works; `(field.value as MutableList).add(item)` bypasses state tracking, validation and
recomposition. The same applies to data class fields: use `copy`.

### 9. The schema type is fixed per form

A form extends `FormDefinition<S>` with a single `S`. All fields of that form use schemas of type `S`. Mixing libraries
inside one form requires a custom `S` (for example a sealed interface wrapping each library's schema).

### 10. `Modifier.onBlur` only fires after the element had focus

If a field is never focused, `blur()` is never called. This is why `submit()` marks every field as touched.

### 11. The schema block runs once

`formField("") { schema }` builds the schema one time, when the form is created. To change rules dynamically assign
`field.schema = newSchema` (then call `field.validate()`), or put the condition inside the rule.

---

## FAQ

**Can I use it without Compose UI, in a shared ViewModel?**
Yes for the state part (`FormDefinition` only needs Compose runtime snapshot state). `Modifier.onBlur` requires Compose UI.

**How do I get all form values as a map?**
`form.fields.mapValues { it.value.value }` gives `Map<String, Any?>` keyed by property name.

**How do I validate a single field on demand?**
`form.email.validate()`.

**How do I show all errors in a summary?**
`form.validate()` returns `Map<fieldName, message>`. For nested/list forms use an `allErrors()` helper as shown in
[Nested fields](nested-fields.md) and [Lists](lists.md).

**How do I mark a field as required when it is a nullable type?**
Declare it as `formField<T?>(null)` and write the rule against `T?` (`constrain("Required") { it != null }` in Konform,
no `.nullable()` call in Kanyon).

**Can two fields validate each other?**
Yes, read the other field in the rule and call `other.validate()` when this one changes, see
[cross-field validation](validation-behaviour.md#cross-field-validation).

**Can I use several validation libraries in one project?**
Yes: create one base class per library (`KonformFormDefinition`, `KanyonFormDefinition`) and pick one per form.

**Does it work with Kotlin Multiplatform?**
The library only uses Compose runtime/UI and Kotlin stdlib. Whether a given form works on a target depends on your
validation library (Konform and Kanyon are both multiplatform).

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| `ClassCastException` in `validateField` | The schema type does not match the field type (e.g. `Validation<String>` on an `Int` field) |
| Kanyon says "Value must be an int" for an `Int` field | `null` value, or the field was declared with another type (`Long`/`Double`); Kanyon's type check is strict |
| Kanyon says "Value must be a string" for a nullable `String?` field with `null` | Add `.nullable()` to the schema |
| Error never appears | Not gating on the right state: display when `touched && error != null`; also check `validateOnChange` / `validateOnBlur` |
| Errors appear before the user types | Missing `touched` check in the UI |
| Nothing recomposes when I add to a list | Mutating the list in place; assign a new list or use `mutableStateListOf` |
| Second sub-form shows no errors after submit | Short-circuit `&&` between `validate()` calls; compute all results first |
| `NullPointerException` / missing key when reading a field | Property declared without `by formField(...)`, or declared after a field that reads it during construction |
| Row state jumps to another row after removal | Missing stable `key` in `LazyColumn` / `key(...)` in `Column` |
| `onBlur` never fires | The modifier is on a non-focusable parent; put it on the text field |
