# 6. Lists of fields

There are three common list scenarios:

| Scenario | Example | Pattern |
|----------|---------|---------|
| [List of primitives](#1-list-of-primitives) | tags, emails | One field whose value is a `List<String>` |
| [List of objects, validated as a whole](#2-list-of-objects-validated-as-a-whole) | selected products, picked contacts | One field whose value is a `List<Item>` |
| [Dynamic list of sub-forms](#3-dynamic-list-of-sub-forms) | phone numbers, line items, each with its own inputs and errors | A `mutableStateListOf<SubForm>` |

> **Always replace the list, never mutate it in place** when it is the value of a field.
> `field.value = field.value + item` triggers recomposition and validation; `(field.value as MutableList).add(item)` does neither.

---

## 1. List of primitives

```kotlin
class ArticleForm : KonformFormDefinition() {
    val title by formField("") { Validation<String> { minLength(1) hint "Title is required" } }

    val tags by formField(emptyList<String>()) {
        Validation<List<String>> {
            minItems(1) hint "Add at least one tag"
            maxItems(5) hint "At most 5 tags"
            onEach {
                minLength(2) hint "Each tag needs at least 2 characters"
            }
        }
    }

    fun addTag(tag: String) {
        val clean = tag.trim()
        if (clean.isNotEmpty() && clean !in tags.value) tags.value = tags.value + clean
    }

    fun removeTag(tag: String) {
        tags.value = tags.value - tag
    }

    override fun onSubmit() {}
}
```

UI:

```kotlin
@Composable
fun TagsEditor(form: ArticleForm) {
    var draft by remember { mutableStateOf("") }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("New tag") },
                modifier = Modifier.weight(1f).onBlur { form.tags.blur() }
            )
            IconButton(onClick = { form.addTag(draft); draft = "" }) {
                Icon(Icons.Default.Add, contentDescription = "Add tag")
            }
        }

        FlowRow {
            form.tags.value.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, "Remove", Modifier.clickable { form.removeTag(tag) })
                    }
                )
            }
        }

        if (form.tags.touched) form.tags.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
```

### Kanyon version

```kotlin
val tags by formField(emptyList<String>()) {
    FieldSchema()
        .isOf<List<*>>("Tags must be a list")
        .validateHandler({ it.isNotEmpty() }, "Add at least one tag")
        .validateHandler({ it.size <= 5 }, "At most 5 tags")
        .validateHandler(
            { list -> list.all { it is String && it.length >= 2 } },
            "Each tag needs at least 2 characters"
        )
        .build()
}
```

---

## 2. List of objects validated as a whole

Same idea with a list of data classes:

```kotlin
data class LineItem(val sku: String, val quantity: Int)

class OrderForm : KonformFormDefinition() {
    val items by formField(emptyList<LineItem>()) {
        Validation<List<LineItem>> {
            minItems(1) hint "The order is empty"
            onEach {
                LineItem::sku { minLength(1) hint "SKU is required" }
                LineItem::quantity { minimum(1) hint "Quantity must be at least 1" }
            }
        }
    }

    fun updateQuantity(index: Int, quantity: Int) {
        items.value = items.value.mapIndexed { i, item ->
            if (i == index) item.copy(quantity = quantity) else item
        }
    }

    fun removeAt(index: Int) {
        items.value = items.value.filterIndexed { i, _ -> i != index }
    }

    override fun onSubmit() {}
}
```

You get one error for the whole list (the first failing rule). If every row needs its own error message, use the
sub-form approach below.

---

## 3. Dynamic list of sub-forms

When each row has several inputs and **per-row errors**, make each row a form and keep the rows in a Compose state list.

### Row form

```kotlin
private var nextRowId = 0L

class PhoneForm(initialLabel: String = "", initialNumber: String = "") : KonformSubForm() {
    val id: Long = nextRowId++     // stable key for lazy lists (must be Bundle-saveable on Android)

    val label by formField(initialLabel) { Validation<String> { minLength(1) hint "Label is required" } }
    val number by formField(initialNumber) {
        Validation<String> { pattern("^\\+?[0-9]{8,15}$") hint "Invalid phone number" }
    }
}
```

(`KonformSubForm` is defined in [Nested fields](nested-fields.md#1-a-base-class-for-sub-forms).)

### Parent form

```kotlin
import androidx.compose.runtime.mutableStateListOf

class ContactForm(private val onValid: (name: String, phones: List<Pair<String, String>>) -> Unit)
    : KonformFormDefinition() {

    val name by formField("") { Validation<String> { minLength(1) hint "Name is required" } }

    val phones = mutableStateListOf(PhoneForm())      // start with one empty row

    fun addPhone() { phones.add(PhoneForm()) }
    fun removePhone(row: PhoneForm) { phones.remove(row) }

    fun submitAll() {
        val parentErrors = validate()
        val rowErrors = phones.map { it.validate() }      // validate EVERY row (no short-circuit)
        val phonesEmpty = phones.isEmpty()
        if (parentErrors.isEmpty() && rowErrors.all { it.isEmpty() } && !phonesEmpty) onSubmit()
    }

    fun resetAll() {
        reset()
        phones.clear()
        phones.add(PhoneForm())
    }

    /** Errors keyed like "phones[1].number". */
    fun allErrors(): Map<String, String> = buildMap {
        putAll(validate())
        phones.forEachIndexed { i, row ->
            row.validate().forEach { (k, v) -> put("phones[$i].$k", v) }
        }
    }

    override fun onSubmit() = onValid(name.value, phones.map { it.label.value to it.number.value })
}
```

### UI

Use `key(...)` so each row keeps its own state (focus, cursor) when rows are added or removed.
Give each row form a stable `id` (as `PhoneForm.id` does above) and use it as the key.

```kotlin
@Composable
fun ContactScreen() {
    val form = remember { ContactForm { name, phones -> /* save */ } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { FormTextField(form.name, label = "Name") }

        itemsIndexed(form.phones, key = { _, row -> row.id }) { index, row ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Phone ${index + 1}", Modifier.weight(1f))
                        IconButton(
                            onClick = { form.removePhone(row) },
                            enabled = form.phones.size > 1
                        ) { Icon(Icons.Default.Delete, "Remove") }
                    }
                    FormTextField(row.label, label = "Label")
                    FormTextField(row.number, label = "Number", keyboardType = KeyboardType.Phone)
                }
            }
        }

        item {
            OutlinedButton(onClick = { form.addPhone() }) { Text("Add phone") }
        }

        item {
            Button(onClick = { form.submitAll() }, Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}
```

> Using a `Column` with `form.phones.forEach { row -> key(row) { ... } }` also works for short lists.
> In a `LazyColumn`, provide a stable key as above so item state is not mixed up on removal.

### Reordering

```kotlin
fun movePhone(from: Int, to: Int) {
    if (from in phones.indices && to in phones.indices) phones.add(to, phones.removeAt(from))
}
```

### Min / max rows

Enforce it in your own `submitAll()` and disable the UI controls accordingly:

```kotlin
val canAdd get() = phones.size < 5
val canRemove get() = phones.size > 1
```

To surface "At least one phone is required" as a form-level error, keep a plain observable property:

```kotlin
var phonesError by mutableStateOf<String?>(null)

fun submitAll() {
    phonesError = if (phones.isEmpty()) "Add at least one phone" else null
    // ...
}
```

### List validation + row validation together

If you want the list-level rule to be enforced by `validate()` rather than by your own code, expose the **count** as a
field and keep it in sync:

```kotlin
val phoneCount by formField(1) { Validation<Int> { minimum(1) hint "Add at least one phone" } }

fun addPhone() { phones.add(PhoneForm()); phoneCount.value = phones.size }
fun removePhone(row: PhoneForm) { phones.remove(row); phoneCount.value = phones.size }
```

---

## Nested lists (a list inside each row)

Rows can contain lists too: the row form simply has its own `mutableStateListOf`, and its `validate` helper recurses.

```kotlin
class SectionForm : KonformSubForm() {
    val title by formField("") { Validation<String> { minLength(1) hint "Title is required" } }
    val questions = mutableStateListOf(QuestionForm())

    fun validateAll(): Boolean {
        val own = validate()
        val nested = questions.map { it.validate() }
        return own.isEmpty() && nested.all { it.isEmpty() }
    }
}

class SurveyForm : KonformFormDefinition() {
    val name by formField("") { Validation<String> { minLength(1) hint "Name is required" } }
    val sections = mutableStateListOf(SectionForm())

    fun submitAll() {
        val own = validate()
        val sectionResults = sections.map { it.validateAll() }   // list first, then check
        if (own.isEmpty() && sectionResults.all { it }) onSubmit()
    }

    override fun onSubmit() {}
}
```
