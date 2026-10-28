# 3. Fields and state

## Declaring a field

```kotlin
val name by formField(
    initialValue = "",
    error = null           // optional: start with an error already set
) {
    /* schema block: returns S */
}
```

| Parameter | Description |
|---|---|
| `initialValue` | Starting value; also what `reset()` restores. Its type becomes `T`. |
| `error` | Optional starting error. Useful for pre-filling server-side errors. |
| trailing lambda | Returns the schema. Optional: omit it for a field with no validation. |

A field without a schema is valid and never produces an error:

```kotlin
val notes by formField("")   // no validation
```

> The schema block runs **once**, when the field is created. Inside it you can reference other properties of the form (see [cross-field validation](recipes.md#cross-field-validation)), but the schema *object* is built a single time.

## Reading and writing values

```kotlin
form.name.value = "Ada"      // write: validates if validateOnChange is true
val current = form.name.value // read
```

Values are Compose state. Reading `value` in a composable subscribes that composable to changes.

## Errors

```kotlin
form.name.error            // String? (null = valid)
form.name.error = "Taken"  // you can set an error manually (e.g. from a server)
```

`error` is also Compose state.

## Validation timing in detail

| Trigger | Condition | Effect |
|---|---|---|
| `field.value = x` | `form.validateOnChange == true` | `validate()` runs after the value is stored |
| `field.blur()` | `form.validateOnBlur == true` | `validate()` runs |
| `field.validate()` | always | validates this field right now |
| `form.validate()` | always | validates **all** registered fields |
| `form.submit()` | always | `form.validate()`, then `onSubmit()` if all valid |

`validateOnChange`/`validateOnBlur` are read from the form that **declares the field**. In a [sub-form](nested-forms.md) that is the sub-form, not the root, so each sub-form has its own flags.

A common UX pattern is "don't nag while typing, but validate on blur, and re-validate on change once an error is showing". You can get this with a small amount of glue code:

```kotlin
class SignUpForm : KonformFormDefinition() {
    init {
        validateOnChange = false
        validateOnBlur = true
    }
    // ...
}

// in the composable:
OutlinedTextField(
    modifier = Modifier.onBlur { form.email.blur() },
    value = form.email.value,
    onValueChange = {
        form.email.value = it
        if (form.email.error != null) form.email.validate() // fix errors live
    },
    // ...
)
```

## Field types

The field type is whatever you pass as `initialValue`. All examples below use Konform; with Kanyon swap in the equivalent `FieldSchema()` chain.

### Text

```kotlin
val title by formField("") { Validation<String> { notBlank() } }

@Composable
fun TitleInput(form: PostForm) = OutlinedTextField(
    modifier = Modifier.onBlur { form.title.blur() },
    value = form.title.value,
    onValueChange = { form.title.value = it },
    isError = form.title.error != null,
    supportingText = form.title.error?.let { { Text(it) } }
)
```

### Numbers

Keep the **form value typed** and convert at the UI edge. Two approaches:

**A. Store the number as text, parse on submit.** Simplest, and the user can type partial input like `"1."`.

```kotlin
val price by formField("") {
    Validation<String> {
        pattern("^\\d+(\\.\\d{1,2})?$") hint "Enter a valid price"
    }
}

override fun onSubmit() {
    val priceValue = price.value.toDouble()
}
```

**B. Store a real `Int`/`Double`, parse in `onValueChange`.**

```kotlin
val quantity by formField(1) {
    Validation<Int> {
        minimum(1) hint "At least 1"
        maximum(99) hint "At most 99"
    }
}

OutlinedTextField(
    value = form.quantity.value.toString(),
    onValueChange = { text ->
        text.toIntOrNull()?.let { form.quantity.value = it }
    },
    isError = form.quantity.error != null,
    supportingText = form.quantity.error?.let { { Text(it) } }
)
```

Option B cannot represent an empty text box. Use a nullable type (`formField<Int?>(null)`) if you need that:

```kotlin
val quantity by formField<Int?>(null) {
    Validation<Int?> {
        constrain("Quantity is required") { it != null }
    }
}
```

> Kanyon users: `Int?` fields need `.nullable()` and then rules are skipped for `null`, so add a required check yourself or keep the field non-null.

### Boolean (checkbox, switch)

```kotlin
val acceptTerms by formField(false) {
    Validation<Boolean> { constrain("You must accept the terms") { it } }
}

Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(
        checked = form.acceptTerms.value,
        onCheckedChange = { form.acceptTerms.value = it }
    )
    Text("I accept the terms")
}
form.acceptTerms.error?.let {
    Text(it, color = MaterialTheme.colorScheme.error)
}
```

There is no focus event for a checkbox, so rely on `validateOnChange` (or `submit()`).

### Enum / selection

```kotlin
enum class Role { Admin, Editor, Viewer }

val role by formField<Role?>(null) {
    Validation<Role?> { constrain("Choose a role") { it != null } }
}

Role.entries.forEach { r ->
    Row(
        Modifier.fillMaxWidth().clickable { form.role.value = r },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = form.role.value == r, onClick = { form.role.value = r })
        Text(r.name)
    }
}
```

### Dropdown

```kotlin
val country by formField("") { Validation<String> { notBlank() hint "Select a country" } }

var expanded by remember { mutableStateOf(false) }
ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
    OutlinedTextField(
        modifier = Modifier.menuAnchor(),
        readOnly = true,
        value = form.country.value,
        onValueChange = {},
        isError = form.country.error != null
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf("DZ", "FR", "US").forEach { code ->
            DropdownMenuItem(
                text = { Text(code) },
                onClick = { form.country.value = code; expanded = false }
            )
        }
    }
}
```

### Collections of simple values

A field can hold an immutable `List`. Always **replace** the list (never mutate it), so the state change is observed.

```kotlin
val tags by formField(emptyList<String>()) {
    Validation<List<String>> {
        minItems(1) hint "Add at least one tag"
        maxItems(5) hint "At most 5 tags"
        onEach { notBlank() hint "Tags cannot be blank" }
    }
}

form.tags.value = form.tags.value + "kotlin"                 // add
form.tags.value = form.tags.value.filterNot { it == "old" }  // remove
```

For lists where each item has several fields, see [Dynamic lists](dynamic-lists.md).

## Changing the schema at runtime

`schema` is Compose state, so you can swap it. This is how conditional validation works:

```kotlin
class ShippingForm : KonformFormDefinition() {
    val sameAsBilling by formField(true)
    val address by formField("") {
        Validation<String> { notBlank() hint "Address is required" }
    }
    val note by formField("")
}

// When the checkbox changes:
form.sameAsBilling.value = checked
form.address.schema = if (checked) null else Validation<String> { notBlank() }
form.address.error = null
```

Setting `schema = null` means "no rules": the field is always valid.

## Reading all values

There is no `values` map. Read the properties you declared. That keeps everything typed:

```kotlin
override fun onSubmit() {
    val dto = SignUpRequest(
        username = username.value,
        email = email.value,
        age = age.value
    )
}
```

## Next

- [Nested forms](nested-forms.md)
