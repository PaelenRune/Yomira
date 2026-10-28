// Test-only helper: parses DEX type/method descriptors from the checked-in
// dexdump-derived inventory so tests can resolve them reflectively.
package com.yomira.core.extension.api

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

internal object DexDescriptors {

    /** A member reference as stored in inventory files: `owner->name(sig)ret` or `owner->name:type`. */
    data class MemberRef(val owner: String, val name: String, val params: List<String>, val ret: String?, val type: String?, val isExecutable: Boolean)

    fun parse(entry: String): MemberRef {
        val arrow = entry.indexOf("->")
        require(arrow > 0) { "Bad member entry: $entry" }
        val owner = entry.substring(0, arrow)
        val rest = entry.substring(arrow + 2)
        return if (rest.contains('(')) {
            val open = rest.indexOf('(')
            val close = rest.indexOf(')')
            val params = mutableListOf<String>()
            var i = open + 1
            while (i < close) {
                var j = i
                while (rest[j] == '[') j++
                if (rest[j] == 'L') j = rest.indexOf(';', j)
                params += rest.substring(i, j + 1)
                i = j + 1
            }
            MemberRef(owner, rest.substring(0, open), params, rest.substring(close + 1), null, true)
        } else {
            val colon = rest.indexOf(':')
            require(colon > 0) { "Bad member entry: $entry" }
            MemberRef(owner, rest.substring(0, colon), emptyList(), null, rest.substring(colon + 1), false)
        }
    }

    fun loadClass(descriptor: String): Class<*> = when (descriptor) {
        "V" -> Void.TYPE
        "Z" -> java.lang.Boolean.TYPE
        "B" -> java.lang.Byte.TYPE
        "C" -> java.lang.Character.TYPE
        "S" -> java.lang.Short.TYPE
        "I" -> java.lang.Integer.TYPE
        "J" -> java.lang.Long.TYPE
        "F" -> java.lang.Float.TYPE
        "D" -> java.lang.Double.TYPE
        else -> when {
            descriptor.startsWith("[") -> Class.forName(descriptor.replace('/', '.'))
            descriptor.startsWith("L") && descriptor.endsWith(";") ->
                Class.forName(descriptor.substring(1, descriptor.length - 1).replace('/', '.'))
            else -> error("Bad type descriptor: $descriptor")
        }
    }

    /** Owner class plus every transitive superclass and superinterface. */
    fun searchSpace(owner: Class<*>): List<Class<*>> {
        val seen = LinkedHashSet<Class<*>>()
        fun visit(type: Class<*>?) {
            if (type == null || !seen.add(type)) return
            visit(type.superclass)
            type.interfaces.forEach(::visit)
        }
        visit(owner)
        return seen.toList()
    }

    fun resolveMethod(ref: MemberRef): Method? {
        val params = ref.params.map(::loadClass).toTypedArray()
        for (type in searchSpace(loadClass(ref.owner))) {
            try {
                return type.getDeclaredMethod(ref.name, *params).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                // keep searching
            }
        }
        return null
    }

    fun resolveConstructor(ref: MemberRef): Constructor<*>? {
        val params = ref.params.map(::loadClass).toTypedArray()
        return try {
            loadClass(ref.owner).getDeclaredConstructor(*params).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    fun resolveField(ref: MemberRef): Field? {
        for (type in searchSpace(loadClass(ref.owner))) {
            try {
                return type.getDeclaredField(ref.name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                // keep searching
            }
        }
        return null
    }

    fun resolve(ref: MemberRef): Any? = when {
        !ref.isExecutable -> resolveField(ref)
        ref.name == "<init>" -> resolveConstructor(ref)
        else -> resolveMethod(ref)
    }
}
