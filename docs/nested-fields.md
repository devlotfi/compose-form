# 5. Nested fields

A form often has grouped data: a `User` with an `Address`, a `Company` with a `Contact`, and so on.
compose-form fields are flat (one entry per property), so nesting is done with two complementary patterns:

| Pattern | Use when | Errors |
|---------|----------|--------|
| [A. Object-valued field](#pattern-a-object-valued-field) | The nested data is validated as a whole and shown with one message, or the object is edited by a single custom control | One message per field |
| [B. Nested sub-form](#pattern-b-nested-sub-form) | Each nested property needs its own input, error and touched state | One error per nested property |

---

## Pattern A: Object-valued field

The field's type `T` is a data class. Always **replace** the object (`copy`) instead of mutating it, so Compose sees
the change.

```kotlin
data class Address(val street: String = "", val city: String = "", val zip: String = "")

class ProfileForm : KonformFormDefinition() {
    val name by formField("") { Validation<String> { minLength(1) hint "Required" } }

    val address by formField(Address()) {
        Validation<Address> {
            Address::street {
                minLength(3) hint "Street is too short"
            }
            Address::city {
                minLength(1) hint "City is required"
            }
            Address::zip {
                pattern("[0-9]{5}") hint "ZIP must have 5 digits"
            }
        }
    }

    override fun onSubmit() {}
}
```

UI: update with `copy`.

```kotlin
OutlinedTextField(
    value = form.address.value.city,
    onValueChange = { form.address.value = form.address.value.copy(city = it) },
    label = { Text("City") },
    isError = form.address.touched && form.address.error != null,
    modifier = Modifier.onBlur { form.address.blur() }
)
// one shared message for the whole address
if (form.address.touched) form.address.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
```

Because Konform returns only the **first** error, the user sees one message at a time for the whole address.
That is fine for compact controls, but if you want an error under each input, use Pattern B.

### With Kanyon

Kanyon validates nested objects through `isOf<T>()` and a custom handler (it reports one message per field):

```kotlin
class ProfileForm : KanyonFormDefinition() {
    val address by formField(AddressModel()) {
        FieldSchema()
            .isOf<AddressModel>()
            .validateHandler({ it.valid() }, "Address is invalid")
            .build()
    }
    override fun onSubmit() {}
}

// AddressModel extends Kanyon's ValidatedObject:
class AddressModel : ValidatedObject() {
    var city by validatedProperty("", FieldSchema().string().notBlank().build())
    var zip by validatedProperty("", FieldSchema().string().regex(Regex("[0-9]{5}")).build())
}
```

> `ValidatedObject` properties are mutable and not Compose state. If you use them as the field value, replace the whole
> object (or assign `form.address.value = form.address.value` after mutating a copy) to trigger recomposition.
> For editable nested inputs, prefer Pattern B.

---

## Pattern B: Nested sub-form

Model each nested group as **its own `FormDefinition`**. It has its own fields, errors and touched flags, and
the parent form composes it.

### 1. A base class for sub-forms

A sub-form does not submit by itself, so give it an empty `onSubmit`:

```kotlin
abstract class KonformSubForm : KonformFormDefinition() {
    override fun onSubmit() { /* sub-forms are submitted by their parent */ }
}
```

### 2. Define sub-form and parent

```kotlin
class AddressForm : KonformSubForm() {
    val street by formField("") { Validation<String> { minLength(3) hint "Street is too short" } }
    val city by formField("") { Validation<String> { minLength(1) hint "City is required" } }
    val zip by formField("") { Validation<String> { pattern("[0-9]{5}") hint "ZIP must have 5 digits" } }

    fun toAddress() = Address(street.value, city.value, zip.value)
}

class ProfileForm(private val onValid: (name: String, address: Address) -> Unit) : KonformFormDefinition() {

    val name by formField("") { Validation<String> { minLength(1) hint "Name is required" } }

    // nested form: just a property (not a formField)
    val address = AddressForm()

    /** Validates the parent AND every nested form, then submits. */
    fun submitAll() {
        val parentErrors = validate()           // evaluate both before deciding
        val nestedErrors = address.validate()   // so every field gets marked as touched
        if (parentErrors.isEmpty() && nestedErrors.isEmpty()) onSubmit()
    }

    fun resetAll() {
        reset()
        address.reset()
    }

    /** All errors in one map, with dotted keys for nested ones. */
    fun allErrors(): Map<String, String> = buildMap {
        putAll(validate())
        address.validate().forEach { (k, v) -> put("address.$k", v) }
    }

    override fun onSubmit() = onValid(name.value, address.toAddress())
}
```

Important points:

- `submit()` of the parent only validates the parent's **own** fields. That is why we add `submitAll()`.
  (`submit()` is final and cannot be overridden.)
- Call `validate()` on **all** forms before checking results. Using `a.validate().isEmpty() && b.validate().isEmpty()`
  would short-circuit and leave the second form's errors hidden.
- The nested form is a normal property, **not** a `formField`, so it does not appear in the parent's `fields`.

### 3. UI

```kotlin
@Composable
fun ProfileScreen() {
    val form = remember { ProfileForm { name, address -> /* save */ } }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormTextField(form.name, label = "Name")

        Text("Address", style = MaterialTheme.typography.titleMedium)
        FormTextField(form.address.street, label = "Street")
        FormTextField(form.address.city, label = "City")
        FormTextField(form.address.zip, label = "ZIP")

        Button(onClick = { form.submitAll() }) { Text("Save") }
    }
}
```

`FormTextField` is a reusable component defined in [Compose UI patterns](compose-ui-patterns.md#reusable-field-components).

### Multiple levels

Sub-forms can contain sub-forms, apply the same rule recursively. A small generic abstraction keeps it tidy:

```kotlin
interface FormNode {
    fun validateAll(): Map<String, String>
    fun resetAll()
}

abstract class KonformNodeForm : KonformFormDefinition(), FormNode {
    /** Nested forms, keyed by the name used as the error prefix. */
    protected open val children: Map<String, FormNode> = emptyMap()

    override fun validateAll(): Map<String, String> = buildMap {
        putAll(validate())
        children.forEach { (prefix, child) ->
            child.validateAll().forEach { (k, v) -> put("$prefix.$k", v) }
        }
    }

    override fun resetAll() {
        reset()
        children.values.forEach { it.resetAll() }
    }

    fun submitAll() { if (validateAll().isEmpty()) onSubmit() }
}
```

> `children` must be initialised after the nested forms exist. Declare it as a `by lazy`-style getter
> (`override val children get() = mapOf("address" to address)`) to avoid initialisation-order surprises.

### Mixing patterns

You can combine both: an `AddressForm` sub-form for editing, and an `Address` object produced by `toAddress()` when
submitting, as above. Pattern A is useful when the nested value comes from a **picker** (for instance a location
chooser that returns a whole `Address`), Pattern B when the user types each part.
