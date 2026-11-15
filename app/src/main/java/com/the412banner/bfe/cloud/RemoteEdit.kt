package com.the412banner.bfe.cloud

/** What saving the edit form changes: rclone settings to write, and the start folder (null = keep). */
data class RemoteEdit(val changes: Map<String, String>, val startPath: String?)

/**
 * Compares the edit form [values] with the remote's [saved] settings. A password left empty is kept;
 * an emptied number goes back to its default (rclone can't read an empty number); a field with a
 * [ServiceField.blankValue] left empty gets that value, as when adding.
 */
fun remoteEdit(fields: List<ServiceField>, saved: Map<String, String>, values: Map<String, String>): RemoteEdit {
    val changes = HashMap<String, String>()
    var startPath: String? = null
    for (f in fields) {
        val v = values[f.key].orEmpty().trim()
        when {
            f.key == CloudServices.START_PATH_KEY -> startPath = v.trim('/')
            f.kind == FieldKind.PASSWORD -> if (v.isNotEmpty()) changes[f.key] = v
            else -> {
                val new = v.ifEmpty { f.blankValue ?: if (f.kind == FieldKind.NUMBER) f.default else "" }
                val old = saved[f.key] ?: f.default
                if (new != old) changes[f.key] = new
            }
        }
    }
    return RemoteEdit(changes, startPath)
}

/** The edit form's starting values from the saved settings (passwords are never shown). */
fun remoteEditValues(fields: List<ServiceField>, saved: Map<String, String>, startPath: String): Map<String, String> =
    fields.associate { f ->
        f.key to when {
            f.kind == FieldKind.PASSWORD -> ""
            f.key == CloudServices.START_PATH_KEY -> startPath
            saved.containsKey(f.key) -> saved.getValue(f.key).takeUnless { it == f.blankValue }.orEmpty()
            else -> f.default
        }
    }
