package app.aaps.core.keys.interfaces

interface StringPreferenceKey : PreferenceKey, StringNonPreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    override val defaultValue: String
    val isPassword: Boolean
    val isPin: Boolean
}