# 9. Limitations and gotchas

An honest list of behaviours to know about, with workarounds. Several are good candidates for future library improvements.

## 1. Always call `dispose()` when discarding a sub-form

Removing a sub-form from a `mutableStateListOf` only changes the UI. Its fields stay on the root's `fields` set and keep blocking `submit()`.

```kotlin
contacts.remove(contact)
contact.dispose()
```

## 2. Registration only reaches the direct root

A field is registered on its own form and on that form's `root`. A grandchild registered with `parent = child` is **not** seen by the top-level form.

**Workaround:** pass the *top-level* form as `parent` for every sub-form, and dispose grandchildren manually with their parent. See [Nested forms](nested-forms.md#multiple-levels-of-nesting).

*Possible improvement:* have `dispose()` recurse into child forms, or make registration walk up the whole `root` chain.

## 3. `fields` is not observable

`fields` is a plain `mutableSetOf`. Reading `form.fields.size` in a composable will not recompose when fields are added or removed. Use a snapshot state (like the `contacts` list) for anything the UI must react to.

## 4. Schema type must match the field type

For Konform, the adapter casts `Validation<*>` to `Validation<T>`. If `formField("")` (a `String` field) is given a `Validation<Int>`, the cast fails quietly and the field is treated as valid. Keep the type argument identical to the field type.

Kanyon is strict about runtime types in the same spirit: `int()` rejects `Long`, `string()` rejects `null`, etc.

## 5. Schema blocks run once

The block passed to `formField { }` runs when the field is created. To change rules later, assign `field.schema = ...`. Rules can still read other fields' current values at validation time (see [cross-field validation](recipes.md#cross-field-validation)).

## 6. `validate()` and `submit()` are not overridable

Both are final. For extra rules (cross-field checks, list-size checks), add your own method such as `trySubmit()`. See [Recipes](recipes.md).

## 7. No side-effect-free validity check

`validate()` always writes errors into fields. There is no `isValid` getter that leaves `error` untouched, so you cannot, for example, disable a submit button until the form is valid without also showing errors.

**Workaround:** keep the button enabled and show errors on press, or track your own derived state from specific fields.

## 8. Timing flags are per form, not inherited

A sub-form's `validateOnChange`/`validateOnBlur` are its own. Setting them on the root does not affect sub-forms.

## 9. `reset()` does not rebuild lists

`form.reset()` resets field values and errors only. Items added to a list stay, with their values reset. Write a `resetForm()` that also trims the list ([example](submit-and-reset.md#resetting-a-form-with-a-dynamic-list)).

## 10. Only the first error is shown

The Konform adapter shows the first error message. Join them in the adapter if you want all messages.

## 11. `onSubmit()` is synchronous

There is no coroutine scope in the form. Launch from your ViewModel and report results by setting `error` on fields ([Submit and reset](submit-and-reset.md#async-submit-and-loading-state)).

## 12. Forms cannot be stored with `rememberSaveable`

Persist raw values and rebuild the form (see [Recipes](recipes.md#keeping-the-form-alive)).

## 13. `onBlur` needs a real focus change

`Modifier.onBlur` fires when a focused element loses focus. Non-focusable inputs (checkboxes, custom pickers) never trigger it. Rely on `validateOnChange` or `submit()` for those.

## 14. The `KanyonFormDefinition` constructor must use named arguments

`FormDefinition`'s constructor order is `(validateOnChange, validateOnBlur, root)`. Passing `parent` first, positionally, will not compile. Use `FormDefinition<FieldSchema>(validateOnChange = ..., validateOnBlur = ..., root = parent)`.

## Suggested improvements (roadmap ideas)

| Idea | Benefit |
|---|---|
| Track child forms and make `dispose()`/`validate()`/`reset()` recursive | Safe deep nesting, no manual disposal of grandchildren |
| `val isValid: Boolean` (no side effects) | Enable/disable buttons without showing errors |
| `open fun validate()` / `suspend fun onSubmit()` | Cross-field rules and async submit inside the form |
| `FormListDefinition<T>` helper | Built-in add/remove/dispose for lists of sub-forms |
| Observable `fields` | Error summaries without custom getters |
