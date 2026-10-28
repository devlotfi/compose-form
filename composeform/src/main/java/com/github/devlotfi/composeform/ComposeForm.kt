package com.github.devlotfi.composeform

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

fun Modifier.onBlur(
    onBlur: () -> Unit
): Modifier = composed {
    var wasFocused by remember { mutableStateOf(false) }
    Modifier.onFocusChanged { state ->
        if (wasFocused && !state.isFocused) {
            onBlur()
        }
        wasFocused = state.isFocused
    }
}

class FormFieldData<T, S>(
    val initialValue: T,
    error: String?,
    touched: Boolean = false,
    schema: S?,
    val thisRef: FormDefinition<S>
) {
    var error by mutableStateOf(error)
    var schema by mutableStateOf(schema)
    private val _value = mutableStateOf(initialValue)
    var value: T
        get() = _value.value
        set(value) {
            _value.value = value
            if (thisRef.validateOnChange) validate()
        }

    fun blur() {
        if (thisRef.validateOnBlur) validate()
    }

    fun validate() {
        error = thisRef.validateField(value, schema)
    }

    fun reset() {
        value = initialValue
        error = null
    }
}

class FormField<T, S>(val fieldData: FormFieldData<T, S>) :
    ReadOnlyProperty<FormDefinition<S>, FormFieldData<T, S>> {
    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: FormDefinition<S>, property: KProperty<*>): FormFieldData<T, S> {
        return fieldData
    }
}

abstract class FormDefinition<S>(
    var validateOnChange: Boolean = true,
    var validateOnBlur: Boolean = true,
    val root: FormDefinition<S>? = null
) {
    val fields = mutableSetOf<FormFieldData<*, *>>()

    fun <T> formField(
        initialValue: T,
        error: String? = null,
        touched: Boolean = false,
        schemaBlock: (() -> S)? = null
    ) =
        PropertyDelegateProvider<FormDefinition<S>, FormField<T, S>> { thisRef, property ->
            val formFieldData = FormFieldData(
                initialValue = initialValue,
                error = error,
                touched = touched,
                schema = if (schemaBlock != null) schemaBlock() else null,
                thisRef = thisRef
            )
            val formField = FormField<T, S>(formFieldData)
            if (root != null) root.fields.add(formFieldData)
            fields.add(formFieldData)
            return@PropertyDelegateProvider formField
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

    fun reset() {
        fields.forEach { field ->
            field.reset()
        }
    }

    fun dispose() {
        if (root != null) root.fields.removeAll(fields)
    }
}