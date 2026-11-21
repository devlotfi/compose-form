package com.github.devlotfi.composeform

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun Modifier.onBlur(onBlur: () -> Unit): Modifier = composed {
  var wasFocused by remember { mutableStateOf(false) }
  Modifier.onFocusChanged { state ->
    if (wasFocused && !state.isFocused) onBlur()
    wasFocused = state.isFocused
  }
}

/** Convenience: wires blur validation straight to the field. */
fun Modifier.onBlur(field: FormFieldData<*, *>): Modifier = onBlur { field.blur() }

/* ---------------------------- Field base ---------------------------- */
abstract class FormFieldData<T, S>(
  initialError: String?,
  initialSchema: S?,
  val thisRef: FormDefinition<S>
) {
  var error by mutableStateOf(initialError)
  var schema by mutableStateOf(initialSchema)

  abstract val value: T
  protected abstract fun resetValue()

  fun blur() {
    if (thisRef.validateOnBlur) validate()
  }

  fun validate() {
    error = thisRef.validateField(value, schema)
  }

  fun reset() {
    resetValue()
    error = null
  }
}

class ValueFormFieldData<T, S>(
  val initialValue: T,
  initialError: String?,
  initialSchema: S?,
  thisRef: FormDefinition<S>
) : FormFieldData<T, S>(initialError, initialSchema, thisRef) {
  private val _value = mutableStateOf(initialValue)

  override var value: T
    get() = _value.value
    set(v) {
      _value.value = v
      if (thisRef.validateOnChange) validate()
    }

  override fun resetValue() {
    _value.value = initialValue
  }
}

class TextFormFieldData<S>(
  val initialValue: String,
  initialError: String?,
  initialSchema: S?,
  thisRef: FormDefinition<S>
) : FormFieldData<String, S>(initialError, initialSchema, thisRef) {

  val state = TextFieldState(initialValue)

  val inputTransformation = InputTransformation {
    // `this` is TextFieldBuffer here, so delegate to the field
    if (thisRef.validateOnChange) validateText(asCharSequence().toString())
  }

  private fun validateText(text: String) {
    error = thisRef.validateField(text, schema)
  }

  override val value: String get() = state.text.toString()

  fun setValue(newValue: String) {
    state.setTextAndPlaceCursorAtEnd(newValue)
    if (thisRef.validateOnChange) validate()
  }

  override fun resetValue() {
    state.setTextAndPlaceCursorAtEnd(initialValue)
  }
}

/* ------------------------------ Delegate ------------------------------ */

class FormField<S, F : FormFieldData<*, S>>(val fieldData: F) :
  ReadOnlyProperty<FormDefinition<S>, F> {
  override fun getValue(thisRef: FormDefinition<S>, property: KProperty<*>): F = fieldData
}

/* ----------------------------- Definition ----------------------------- */

abstract class FormDefinition<S>(
  var validateOnChange: Boolean = true,
  var validateOnBlur: Boolean = true,
  val root: FormDefinition<S>? = null
) {
  val fields = mutableSetOf<FormFieldData<*, *>>()

  private fun <F : FormFieldData<*, S>> register(data: F): FormField<S, F> {
    root?.fields?.add(data)
    fields.add(data)
    return FormField(data)
  }

  /** Non-text values */
  fun <T> formField(
    initialValue: T,
    error: String? = null,
    schemaBlock: (() -> S)? = null
  ) =
    PropertyDelegateProvider<FormDefinition<S>, FormField<S, ValueFormFieldData<T, S>>> { thisRef, _ ->
      register(ValueFormFieldData(initialValue, error, schemaBlock?.invoke(), thisRef))
    }

  /** Text inputs backed by TextFieldState */
  fun textField(
    initialValue: String = "",
    error: String? = null,
    schemaBlock: (() -> S)? = null
  ) =
    PropertyDelegateProvider<FormDefinition<S>, FormField<S, TextFormFieldData<S>>> { thisRef, _ ->
      register(TextFormFieldData(initialValue, error, schemaBlock?.invoke(), thisRef))
    }

  abstract fun <T> validateField(value: T, schema: S?): String?
  open fun onSubmit() {}

  fun validate(): Boolean {
    var valid = true
    fields.forEach { field ->
      field.validate()
      if (field.error != null) valid = false
    }
    return valid
  }

  fun submit() {
    if (validate()) onSubmit()
  }

  fun reset() = fields.forEach { it.reset() }

  fun dispose() {
    root?.fields?.removeAll(fields)
  }
}