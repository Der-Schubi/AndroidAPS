package app.aaps.plugins.configuration.setupwizard.elements

import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.ui.elements.NumberPicker
import dagger.android.HasAndroidInjector
import java.text.DecimalFormat

class SWEditNumber(injector: HasAndroidInjector) : SWItem(injector, Type.DECIMAL_NUMBER) {

    private val validator: (Double) -> Boolean = { value -> value in (preference as DoublePreferenceKey).min..(preference as DoublePreferenceKey).max }
    private var updateDelay = 0

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val watcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (validator.invoke(SafeParse.stringToDouble(s.toString())))
                    save(s.toString(), updateDelay.toLong())
            }

            override fun afterTextChanged(s: Editable) {}
        }

        val l = TextView(context)
        l.id = View.generateViewId()
        label?.let { l.setText(it) }
        l.setTypeface(l.typeface, Typeface.BOLD)
        layout.addView(l)
        val initValue = preferences.get(preference as DoublePreferenceKey)
        val numberPicker = NumberPicker(context)
        numberPicker.setParams(initValue, (preference as DoublePreferenceKey).min, (preference as DoublePreferenceKey).max, 0.1, DecimalFormat("0.0"), false, null, watcher)

        layout.addView(numberPicker)
        val c = TextView(context)
        c.id = View.generateViewId()
        comment?.let { c.setText(it) }
        c.setTypeface(c.typeface, Typeface.ITALIC)
        layout.addView(c)
        super.generateDialog(layout)
    }

    fun preference(preference: DoublePreferenceKey): SWEditNumber {
        this.preference = preference
        return this
    }

    fun updateDelay(updateDelay: Int): SWEditNumber {
        this.updateDelay = updateDelay
        return this
    }

}