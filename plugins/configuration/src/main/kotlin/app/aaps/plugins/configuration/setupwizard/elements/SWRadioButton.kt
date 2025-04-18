package app.aaps.plugins.configuration.setupwizard.elements

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import app.aaps.core.keys.interfaces.StringPreferenceKey
import dagger.android.HasAndroidInjector

class SWRadioButton(injector: HasAndroidInjector) : SWItem(injector, Type.RADIOBUTTON) {

    private var labelsArray: Array<CharSequence> = emptyArray()
    private var valuesArray: Array<CharSequence> = emptyArray()
    private var radioGroup: RadioGroup? = null

    fun option(labels: Array<CharSequence>, values: Array<CharSequence>): SWRadioButton {
        labelsArray = labels
        valuesArray = values
        return this
    }

    private fun labels(): Array<CharSequence> {
        return labelsArray
    }

    private fun values(): Array<CharSequence> {
        return valuesArray
    }

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val desc = TextView(context)
        comment?.let { desc.setText(it) }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, 40)
        desc.layoutParams = params
        layout.addView(desc)

        // Get if there is already value in Preferences
        val previousValue = preferences.get(preference as StringPreferenceKey)
        radioGroup = RadioGroup(context)
        radioGroup?.clearCheck()
        radioGroup?.orientation = LinearLayout.VERTICAL
        radioGroup?.visibility = View.VISIBLE
        for (i in labels().indices) {
            val rdBtn = RadioButton(context)
            rdBtn.id = View.generateViewId()
            rdBtn.text = labels()[i]
            if (previousValue == values()[i]) rdBtn.isChecked = true
            rdBtn.tag = i
            radioGroup!!.addView(rdBtn)
        }
        radioGroup!!.setOnCheckedChangeListener { group: RadioGroup, checkedId: Int ->
            val i = group.findViewById<View>(checkedId).tag as Int
            save(values()[i], 0)
        }
        layout.addView(radioGroup)
        super.generateDialog(layout)
    }

    fun preference(preference: StringPreferenceKey): SWRadioButton {
        this.preference = preference
        return this
    }
}