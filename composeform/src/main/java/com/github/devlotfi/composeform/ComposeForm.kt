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
    var initialValue: T,
    error: String?,
    touched: Boolean = false,
    schema: S?,
    val thisRef: FormDefinition<S>
) {
    var error by mutableStateOf(error)
    var touched by mutableStateOf(touched)
    var schema by mutableStateOf(schema)
    private val _value = mutableStateOf(initialValue)
    var value: T
        get() = _value.value
        set(value) {
            touched = true
            _value.value = value
            if (thisRef.validateOnChange) validate()
        }

    init {
        validate()
    }

    fun blur() {
        touched = true
        if (thisRef.validateOnBlur) validate()
    }

    fun validate() {
        error = thisRef.validateField(value, schema)
    }

    fun reset() {
        value = initialValue
        error = null
        touched = false
    }
}

class FormField<T, S>() : ReadOnlyProperty<FormDefinition<S>, FormFieldData<T, S>> {
    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: FormDefinition<S>, property: KProperty<*>): FormFieldData<T, S> {
        return thisRef.fields[property.name] as FormFieldData<T, S>
    }
}

abstract class FormDefinition<S>(
    var validateOnChange: Boolean = true,
    var validateOnBlur: Boolean = true
) {
    val fields = mutableMapOf<String, FormFieldData<*, *>>()

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
            val formField = FormField<T, S>()
            fields[property.name] = formFieldData
            return@PropertyDelegateProvider formField
        }

    abstract fun <T> validateField(value: T, schema: S?): String?
    abstract fun onSubmit()

    fun validate(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        fields.forEach { (key, field) ->
            field.validate()
            field.touched = true
            if (field.error != null) map[key] = field.error as String
        }
        return map
    }

    fun submit() {
        if (validate().isEmpty()) onSubmit()
    }

    fun reset() {
        fields.forEach { (_, field) ->
            field.reset()
        }
    }
}