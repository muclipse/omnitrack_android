package kr.ac.snu.hcil.omnitrack.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kr.ac.snu.hcil.omnitrack.utils.serialization.IStringSerializable
import java.util.*

/**
 * Created by younghokim on 16. 8. 22..
 */
class UniqueStringEntryList : IStringSerializable {

    companion object {
        val parser: Gson by lazy {
            GsonBuilder().registerTypeAdapter(UniqueStringEntryList::class.java, UniqueStringEntryListTypeAdapter()).create()
        }
    }


    internal class UniqueStringEntryListTypeAdapter : TypeAdapter<UniqueStringEntryList>() {
        override fun read(input: JsonReader): UniqueStringEntryList {

            val result = UniqueStringEntryList()

            input.beginObject()
            while (input.hasNext()) {
                when (input.nextName()) {
                    "seed", "i" -> result.increment = input.nextInt()
                    "entries", "e" -> {
                        input.beginArray()
                        while (input.hasNext()) {
                            input.beginObject()
                            while (input.hasNext()) {
                                var id: Int? = null
                                var value: String? = null
                                when (input.nextName()) {
                                    "id", "i" -> id = input.nextInt()
                                    "val", "v" -> value = input.nextString()
                                }

                                if (id != null && value != null)
                                    result.list.add(Entry(id, value))
                            }
                            input.endObject()
                        }
                        input.endArray()
                    }
                }
            }

            input.endObject()



            return result
        }

        override fun write(out: JsonWriter, value: UniqueStringEntryList) {
            out.beginObject()
            out.name("seed").value(value.increment)
            out.name("entries").beginArray()

            value.list.forEach {
                out.beginObject()
                out.name("id").value(it.id)
                out.name("val").value(it.text)
                out.endObject()
            }

            out.endArray()
            out.endObject()
        }

    }


    private data class SerializationParcel(val increment: Int, val entries: Array<Entry>)

    data class Entry(val id: Int, var text: String)

    private var increment = -1
    private val list: ArrayList<Entry>

    protected val getNewId: Int get() {
        return ++increment
    }

    constructor() {
        increment = -1
        list = ArrayList<Entry>()
    }

    constructor(increment: Int, entries: Collection<Entry>) {
        this.increment = increment
        list = ArrayList<Entry>(entries)
    }

    constructor(serialized: String) {
        val parcel = Gson().fromJson(serialized, SerializationParcel::class.java)
        increment = parcel.increment
        list = ArrayList<Entry>(parcel.entries.size)
        list.addAll(parcel.entries)
    }

    constructor(entries: Collection<Entry>) {
        this.increment = entries.size - 1
        list = ArrayList<Entry>(entries)
    }

    constructor(entries: Array<Entry>) {
        this.increment = entries.size - 1
        list = ArrayList<Entry>().apply { addAll(entries) }
    }

    constructor(vararg entryNames: String) {
        list = ArrayList<Entry>(entryNames.size)
        entryNames.map { Entry(getNewId, it) }.toCollection(list)
    }

    override fun equals(other: Any?): Boolean {


        if (this === other) return true
        else if (other is UniqueStringEntryList) {
            return other.increment == this.increment && other.list.isSame(this.list)
        } else return false
    }

    fun set(from: UniqueStringEntryList) {
        this.increment = from.increment
        this.list.clear()
        this.list.addAll(from.list.map { it.copy() })
    }

    fun indexOf(id: Int): Int {
        for (entry in list.withIndex()) {
            if (entry.value.id == id) {
                return entry.index
            }
        }
        return -1
    }

    fun getIds(): Array<Int> {
        return list.map { it.id }.toTypedArray()
    }


    override fun fromSerializedString(serialized: String): Boolean {
        val parcel = Gson().fromJson(serialized, SerializationParcel::class.java)
        println("deserializing ustentrylist")
        println(parcel.entries)
        increment = parcel.increment
        list.addAll(parcel.entries)

        return true
    }

    override fun getSerializedString(): String {
        //return Gson().toJson(SerializationParcel(increment, list.toTypedArray()))
        return parser.toJson(this)
    }


    fun toArray(): Array<UniqueStringEntryList.Entry> {
        return list.toTypedArray()
    }

    fun isEmpty(): Boolean {
        return list.isEmpty()
    }

    fun clone(): UniqueStringEntryList {
        val newList = list.map { Entry(it.id, it.text) }
        return UniqueStringEntryList(increment, newList)
    }

    operator fun iterator(): MutableIterator<Entry> {
        return list.iterator()
    }

    operator fun get(index: Int): Entry {
        return list[index]
    }

    fun findWithId(id: Int): Entry? {
        return list.find { it.id == id }
    }

    val size: Int get() = list.size

    fun appendNewEntry() {
        list.add(Entry(getNewId, ""))
    }

    fun move(fromPosition: Int, toPosition: Int) {
        list.move(fromPosition, toPosition)
    }

    fun filterSelf(func: (Entry) -> Boolean) {

        val it = list.iterator()

        while (it.hasNext()) {
            val entry = it.next()

            if (!func(entry)) {
                it.remove()
            }
        }
    }

    fun filter(func: (Entry) -> Boolean): List<Entry> {
        return list.filter(func)
    }

    fun removeAt(position: Int): Entry {
        return list.removeAt(position)
    }
}