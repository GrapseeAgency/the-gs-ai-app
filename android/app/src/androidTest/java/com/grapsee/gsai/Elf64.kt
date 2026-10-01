package com.grapsee.gsai

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A minimal ELF64 reader, for ONE question: which symbols does this library
 * IMPORT, and which does it EXPORT.
 *
 * WHY IT EXISTS, rather than scanning the file's bytes for names.
 *
 * The first version of the device test read the shipped `libgs_ffi.so` and looked
 * for the strings "execve", "fork", "system" anywhere in it. Measured on the real
 * .so from run 36877377145, that scan found:
 *
 *     fork, system
 *
 * and `nm` says:
 *
 *     $ nm -D --undefined-only libgs_ffi.so | grep -c '\bsystem\b'
 *     0
 *
 * So `system` is a string in the file that is not a symbol -- .rodata, or a
 * substring of a longer name. A byte scan cannot tell an IMPORT from a string, and
 * it therefore accuses a library of calling something it never mentions in any
 * symbol table.
 *
 * It also got the real answer wrong in the other direction. The honest picture:
 *
 *     execve       imported 0
 *     system       imported 0
 *     popen        imported 0
 *     posix_spawn  imported 0
 *     execl        imported 0
 *     fork         imported 1   -- exactly one call site, in ggml_print_backtrace
 *
 * So the parser reads `.dynsym` and asks properly.
 *
 * CROSS-CHECKED, because a parser that returns an empty list would make every
 * assertion downstream pass VACUOUSLY. For the x86_64 build:
 *
 *     this parser          309 imported symbols
 *     nm -D --undefined-only | wc -l   309
 *
 * and 345 dynamic symbol entries in total, which is what `nm -D` counts.
 *
 * SCOPE. ELF64 only, which covers all four ABIs this project ships. It reads
 * section headers rather than the program headers' PT_DYNAMIC, because the
 * section table is where `.dynsym` and its string table are named, and a name is
 * worth more than an index that differs per toolchain.
 */
internal class Elf64(private val bytes: ByteArray) {

    private val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    /** Section headers, as (nameOffset, type, offset, size, link, entsize). */
    private data class Section(
        val nameOffset: Int,
        val type: Int,
        val offset: Long,
        val size: Long,
        val link: Int,
        val entsize: Long,
    )

    val isElf64: Boolean = bytes.size > 6 &&
        bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.code.toByte() &&
        bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
        // `.toInt()` because bytes[] is Byte and Kotlin will not compare a Byte to an
        // Int literal -- it says so:
        //     Elf64.kt:69:9 Operator '==' cannot be applied to 'kotlin.Byte' and
        //     'kotlin.Int'
        // which is the correct complaint and the cheapest possible way to find it.
        bytes[4].toInt() == 2 // EI_CLASS: 2 = ELFCLASS64

    // ELF64 header, little-endian, byte offsets. Written out rather than skipped
    // past, because the first version of this read e_shoff at 16 -- which is
    // e_entry -- and would have read section headers out of the middle of the
    // program. Every offset below is from the spec:
    //
    //   0   e_ident[16]
    //   16  e_type u16     18 e_machine u16   20 e_version u32
    //   24  e_entry u64    32 e_phoff u64     40 e_shoff u64
    //   48  e_flags u32    52 e_ehsize u16    54 e_phentsize u16
    //   56  e_phnum u16    58 e_shentsize u16 60 e_shnum u16  62 e_shstrndx u16
    private val eShoff: Long by lazy {
        buf.position(40)
        buf.long
    }

    private val eShentsize: Int by lazy {
        buf.position(58)
        buf.short.toInt() and 0xFFFF
    }

    private val eShnum: Int by lazy {
        buf.position(60)
        buf.short.toInt() and 0xFFFF
    }

    private val eShstrndx: Int by lazy {
        buf.position(62)
        buf.short.toInt() and 0xFFFF
    }

    private val sections: List<Section> by lazy {
        require(isElf64) {
            "not an ELF64 file: EI_CLASS=${if (bytes.size > 4) bytes[4].toInt() else -1}"
        }
        require(bytes.size >= 64) { "file is ${bytes.size} bytes, too short for an ELF64 header" }
        require(eShnum > 0 && eShentsize >= 64) {
            "no section headers: shnum=$eShnum shentsize=$eShentsize"
        }
        // SHN_XINDEX means the real count lives in section 0. Not handled, and not
        // silently misread either: the require below refuses.
        require(eShnum != 0xFFFF) { "e_shnum is SHN_XINDEX, which this reader does not handle" }
        require(eShoff + eShnum.toLong() * eShentsize <= bytes.size) {
            "section header table runs past the end of the file: " +
                "shoff=$eShoff shnum=$eShnum shentsize=$eShentsize size=${bytes.size}"
        }
        val out = ArrayList<Section>(eShnum)
        for (i in 0 until eShnum) {
            buf.position((eShoff + i.toLong() * eShentsize).toInt())
            out.add(
                Section(
                    nameOffset = buf.int,
                    type = buf.int,
                    offset = buf.long,
                    size = buf.long,
                    link = buf.int,
                    entsize = buf.long,
                )
            )
        }
        require(eShstrndx in out.indices) { "e_shstrndx=$eShstrndx is out of range" }
        out
    }

    private val shstrtab: Section by lazy { sections[eShstrndx] }

    /** The NUL-terminated string at `base + offset`, or "" if that is out of range. */
    private fun cString(base: Long, offset: Int): String {
        var i = base + offset
        if (i < 0L || i >= bytes.size.toLong()) return ""
        var end = i
        while (end < bytes.size && bytes[end.toInt()] != 0.toByte()) end++
        return String(bytes, i.toInt(), (end - i).toInt(), Charsets.ISO_8859_1)
    }

    private fun sectionNamed(name: String): Section? = sections.firstOrNull {
        cString(shstrtab.offset, it.nameOffset) == name
    }

    /** Every entry in `.dynsym`, as (name, isUndefined). */
    private fun dynsymEntries(): List<Pair<String, Boolean>> {
        val dynsym = sectionNamed(".dynsym")
            ?: return emptyList<Pair<String, Boolean>>()
        val dynstr = sections.getOrNull(dynsym.link) ?: return emptyList<Pair<String, Boolean>>()
        val entsize = if (dynsym.entsize > 0) dynsym.entsize else 24L
        if (entsize < 24) return emptyList<Pair<String, Boolean>>()
        val count = dynsym.size / entsize
        val out = ArrayList<Pair<String, Boolean>>(count.toInt())
        // `count.toInt()`, because `until` is Int-only and count is Long. Caught by
        // reading the file against the type signatures rather than by finding out
        // in a run, after the Byte/Int comparison above cost one.
        for (i in 0 until count.toInt()) {
            val off = (dynsym.offset + i * entsize).toInt()
            if (off + 24 > bytes.size) break
            buf.position(off)
            val stName = buf.int
            val stShndx = buf.short.toInt() and 0xFFFF
            val name = cString(dynstr.offset, stName)
            if (name.isNotEmpty()) out.add(name to (stShndx == 0))
        }
        return out
    }

    /** How many entries `.dynsym` holds. Zero means the parse failed. */
    val dynsymCount: Int get() = dynsymEntries().size

    /**
     * Symbols this library must resolve at load time.
     *
     * An entry with `st_shndx == 0` is SHN_UNDEF: undefined here, defined
     * elsewhere. Those are the imports, and they are the only thing that can be
     * called.
     *
     * Any `@`-suffixed version tag is STRIPPED. Android's libc exports
     * `fork@LIBC`, and `nm` prints that suffix -- but it lives in a version table,
     * not in `.dynstr`, so the raw names are already bare. Stripping anyway means a
     * genuinely versioned name cannot make `contains("fork")` false, which would
     * turn an assertion about a real import into a silent pass.
     */
    fun importedSymbols(): Set<String> =
        dynsymEntries().filter { it.second }.map { stripVersion(it.first) }.toSortedSet()

    /** Symbols this library defines and therefore exports. */
    fun exportedSymbols(): Set<String> =
        dynsymEntries().filter { !it.second }.map { stripVersion(it.first) }.toSortedSet()

    private fun stripVersion(name: String): String = name.substringBefore('@')

    /** Debug string for a failure message. */
    fun describe(): String =
        "ELF64, ${sections.size} sections, ${dynsymCount} dynamic symbols, " +
            "machine=${if (bytes.size > 19) (bytes[18].toInt() and 0xFF) or ((bytes[19].toInt() and 0xFF) shl 8) else -1}"
}