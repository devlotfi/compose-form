# 4. Nested forms

Real forms are rarely flat. compose-form models grouping with **sub-forms**: a sub-form is just another `FormDefinition` that is given a *parent*.

## How registration works

Every form keeps a `fields` set. When a field is declared:

1. it is added to the **declaring form's** `fields`, and
2. if the declaring form has a `root` (the `parent` you passed to its constructor), it is **also** added to `root.fields`.

So the root form sees every field of its direct children, and `root.validate()`, `root.submit()` and `root.reset()` operate on all of them at once.

```text
SignUpForm (root)                      fields = { firstName, lastName, street, city }
 ├─ firstName
 ├─ lastName
 └─ address : AddressForm(parent = SignUpForm)
      ├─ street      ← also registered on SignUpForm
      └─ city        ← also registered on SignUpForm
```

## Example: a form with an address group

### 1. Define the sub-form

The sub-form receives its parent and forwards it to the adapter:

```kotlin
class AddressForm(parent: KonformFormDefinition) : KonformFormDefinition(parent = parent) {
    val street by formField("") {
        Validation<String> { notBlank() hint "Street is required" }
    }
    val city by formField("") {
        Validation<String> { notBlank() hint "City is required" }
    }
    val zip by formField("") {
        Validation<String> {
            pattern("^\\d{5}$") hint "ZIP must be 5 digits"
        }
    }
}
```

### 2. Create it inside the root form

Pass `this`:

```kotlin
class CheckoutForm : KonformFormDefinition() {
    val fullName by formField("") {
        Validation<String> { notBlank() hint "Name is required" }
    }

    val address = AddressForm(this)      // <-- parent = this

    override fun onSubmit() {
        println("${fullName.value}, ${address.street.value}, ${address.city.value}")
    }
}
```

### 3. Bind in Compose

Sub-form fields are used exactly like root fields:

```kotlin
@Composable
fun CheckoutScreen(form: CheckoutForm) {
    Column {
        FormTextField(form.fullName, label = "Full name")
        FormTextField(form.address.street, label = "Street")
        FormTextField(form.address.city, label = "City")
        FormTextField(form.address.zip, label = "ZIP")

        Button(onClick = { form.submit() }) { Text("Pay") }
    }
}
```

(`FormTextField` is the reusable component from [Recipes](recipes.md#a-reusable-text-field).)

`form.submit()` validates `fullName` **and** all address fields. If you only want to validate the address group, call `form.address.validate()`. That validates the sub-form's own fields.

## Sub-form with its own submit

A sub-form can be validated on its own (e.g., one step of a wizard):

```kotlin
class WizardForm : KonformFormDefinition() {
    val account = AccountStep(this)
    val profile = ProfileStep(this)
}

// Step 1 → Next button
Button(onClick = { if (form.account.validate()) goToStep(2) }) { Text("Next") }

// Final step → Submit: validates everything
Button(onClick = { form.submit() }) { Text("Finish") }
```

`validate()` returns `true` when **every field of that form** has no error.

## Timing flags are per form

Each sub-form has its own `validateOnChange` / `validateOnBlur`. A sub-form does **not** inherit them from its parent:

```kotlin
class AddressForm(parent: KonformFormDefinition) : KonformFormDefinition(parent = parent) {
    init { validateOnChange = false }   // this group validates on blur only
    // ...
}
```

With `KanyonFormDefinition` you can also pass them through the constructor.

## Multiple levels of nesting

A field registers with **its declaring form and that form's direct `root`** only. This has an important consequence.

| Layout | Does `root.validate()` see the field? |
|---|---|
| Field on the root | Yes |
| Field on a child, `parent = root` | Yes |
| Field on a grandchild, `parent = child` | **No** (registered only on the child) |
| Field on a grandchild, `parent = root` | Yes |

**Rule: always pass the top-level form as `parent`, however deep the sub-form is nested.**

```kotlin
class ContactForm(root: KonformFormDefinition) : KonformFormDefinition(parent = root) {
    val name by formField("") { Validation<String> { notBlank() } }

    // grandchild: gets the ROOT, not "this"
    val address = AddressForm(root)
}

class MainForm : KonformFormDefinition() {
    val contact = ContactForm(this)
}
```

Now `MainForm.validate()` covers `contact.name` and `contact.address.*`.

The flip side: when a mid-level form is **disposed** (removed from a list), its grandchildren stay registered on the root unless you dispose them too. See [Dynamic lists](dynamic-lists.md#nested-sub-forms-inside-list-items).

## Reusing a sub-form in several places

Because sub-forms are plain classes, reuse them:

```kotlin
class OrderForm : KonformFormDefinition() {
    val billing = AddressForm(this)
    val shipping = AddressForm(this)
}
```

`billing.street` and `shipping.street` are separate fields with separate state.

## Next

- [Dynamic lists](dynamic-lists.md)
