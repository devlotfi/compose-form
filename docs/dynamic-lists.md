# 5. Dynamic lists

Use a list when the user can add and remove repeating groups: contacts, phone numbers, line items, education entries, etc.

There are two techniques:

| Technique | Use when |
|---|---|
| **A. List of sub-forms** (`mutableStateListOf<ItemForm>`) | Each item has its own field(s) with their own validation |
| **B. One field holding a `List<T>`** | Items are simple values (tags, selected ids) validated as a whole |

---

## A. List of sub-forms

### 1. The item form

```kotlin
class ContactForm(parent: KonformFormDefinition) : KonformFormDefinition(parent = parent) {
    val name by formField("") {
        Validation<String> {
            notBlank() hint "Name is required"
            minLength(5) hint "At least 5 characters"
        }
    }
    val email by formField("") {
        Validation<String> {
            notBlank() hint "Email is required"
        }
    }
}
```

### 2. The parent form owns a `mutableStateListOf`

```kotlin
class MainForm : KonformFormDefinition() {
    init {
        validateOnChange = true
        validateOnBlur = true
    }

    val firstName by formField("") {
        Validation<String> { notBlank(); minLength(5) }
    }
    val lastName by formField("") {
        Validation<String> { notBlank(); minLength(5) }
    }

    val contacts = mutableStateListOf(ContactForm(this))   // start with one item

    fun addContact() {
        contacts.add(ContactForm(this))
    }

    fun removeContact(contact: ContactForm) {
        contacts.remove(contact)
        contact.dispose()                 // IMPORTANT, see below
    }

    override fun onSubmit() {
        println("Valid form: ${firstName.value} ${lastName.value}")
        contacts.forEach { println("  contact ${it.name.value} <${it.email.value}>") }
    }
}
```

Why `mutableStateListOf`? Compose must be told when the list changes. A normal `MutableList` would add items without recomposing the UI.

### 3. Render with `forEach`/`items`

```kotlin
@Composable
fun ContactsSection(form: MainForm) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Button(onClick = { form.addContact() }) { Text("Add") }
        Button(onClick = { form.contacts.removeLastOrNull()?.dispose() }) { Text("Remove last") }
    }

    form.contacts.forEach { contact ->
        key(contact) {                          // stable identity per item
            Column {
                FormTextField(contact.name, label = "Contact name")
                FormTextField(contact.email, label = "Contact email")
                TextButton(onClick = { form.removeContact(contact) }) { Text("Delete") }
            }
        }
    }
}
```

`key(contact)` matters when items can be removed from the middle: without it, Compose reuses the UI by position and focus/IME state can jump to the wrong row. Inside a `LazyColumn`, use `items(form.contacts, key = { it })` instead.

### Why `dispose()` is required

Every field of `ContactForm` was registered on `MainForm.fields` when the item was created. Removing the item from the **list** only removes it from the UI. Its fields are still in `MainForm.fields`. Consequences if you forget:

- `form.submit()` keeps validating the removed, invisible fields.
- A removed item with an error blocks submission with no visible reason.

`dispose()` unregisters the item's fields from the root:

```kotlin
form.contacts.removeLastOrNull()?.dispose()
```

Always pair "remove from list" with `dispose()`.

### Starting with zero items or with existing data

```kotlin
val contacts = mutableStateListOf<ContactForm>()   // empty

// Pre-fill from existing data (edit screen)
class ContactForm(
    parent: KonformFormDefinition,
    name: String = "",
    email: String = ""
) : KonformFormDefinition(parent = parent) {
    val name by formField(name) { Validation<String> { notBlank() } }
    val email by formField(email) { Validation<String> { notBlank() } }
}

fun load(existing: List<Contact>) {
    existing.forEach { contacts.add(ContactForm(this, it.name, it.email)) }
}
```

Note that `initialValue` is the pre-filled value, so `reset()` restores *that* value.

### Enforcing min/max item count

The count is plain Kotlin logic. Expose it as a derived property and disable buttons accordingly:

```kotlin
val canAdd get() = contacts.size < 5
val canRemove get() = contacts.size > 1
```

```kotlin
Button(enabled = form.canAdd, onClick = { form.addContact() }) { Text("Add") }
```

`contacts.size` is read from snapshot state, so those getters recompose correctly.

For a **validation message** ("At least one contact is required"), check before submitting:

```kotlin
var listError by mutableStateOf<String?>(null)

fun trySubmit() {
    listError = if (contacts.isEmpty()) "Add at least one contact" else null
    if (listError == null) submit()
}
```

### Nested sub-forms inside list items

If each list item itself contains sub-forms (e.g., a contact with an address), pass the **root** to the grandchild and dispose it with the item (see [multiple levels of nesting](nested-forms.md#multiple-levels-of-nesting)):

```kotlin
class ContactForm(private val root: KonformFormDefinition) :
    KonformFormDefinition(parent = root) {

    val name by formField("") { Validation<String> { notBlank() } }
    val address = AddressForm(root)           // registered on the root

    fun disposeAll() {
        address.dispose()
        dispose()
    }
}

class MainForm : KonformFormDefinition() {
    val contacts = mutableStateListOf(ContactForm(this))

    fun removeContact(contact: ContactForm) {
        contacts.remove(contact)
        contact.disposeAll()
    }
}
```

### Reordering

Reordering does not touch field registration, only the list:

```kotlin
fun moveUp(index: Int) {
    if (index > 0) {
        val item = contacts.removeAt(index)
        contacts.add(index - 1, item)
    }
}
```

Field values and errors move with their item, because they live on the item form.

---

## B. One field holding a list

For simple values, no sub-forms are needed:

```kotlin
class ArticleForm : KonformFormDefinition() {
    val tags by formField(emptyList<String>()) {
        Validation<List<String>> {
            minItems(1) hint "Add at least one tag"
            maxItems(5) hint "At most 5 tags"
            onEach {
                notBlank() hint "Tags cannot be blank"
                maxLength(20) hint "Max 20 characters"
            }
        }
    }
}

@Composable
fun TagsInput(form: ArticleForm) {
    var draft by remember { mutableStateOf("") }

    FlowRow {
        form.tags.value.forEach { tag ->
            InputChip(
                selected = true,
                onClick = { form.tags.value = form.tags.value - tag },
                label = { Text(tag) }
            )
        }
    }

    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        isError = form.tags.error != null,
        supportingText = form.tags.error?.let { { Text(it) } }
    )
    Button(onClick = {
        if (draft.isNotBlank()) {
            form.tags.value = form.tags.value + draft.trim()   // new list instance
            draft = ""
        }
    }) { Text("Add tag") }
}
```

Replace the list on every change (`value = value + x`). Mutating a `MutableList` in place does not trigger recomposition or validation.

With **Kanyon**, validate lists with its list/`isOf` support and custom handlers; see the Kanyon page on nested structures: <https://devlotfi.github.io/kanyon/nested-structures.md>.

---

## Choosing between A and B

| Question | A (sub-forms) | B (one field) |
|---|---|---|
| Items have multiple inputs | ✅ | ❌ |
| Per-item error under each input | ✅ | ❌ (one error for the whole list) |
| Items are simple (strings, ids) | works | ✅ simplest |
| Need add/remove with focus handling | ✅ | n/a |

## Next

- [Submit and reset](submit-and-reset.md)
