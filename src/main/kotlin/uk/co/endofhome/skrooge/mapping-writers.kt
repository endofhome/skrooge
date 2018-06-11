package uk.co.endofhome.skrooge

import java.io.File

interface MappingWriter {
    fun write(line: String): Boolean
    fun read(): List<String>
}

class FileSystemMappingWriter : MappingWriter {
    val categoryMappingsFileOutputPath = "category-mappings/category-mappings.csv"
    override fun write(line: String): Boolean {
        try {
            File(categoryMappingsFileOutputPath).appendText(line + "\n")
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun read(): List<String> = File(categoryMappingsFileOutputPath).readLines()
}

class StubbedMappingWriter : MappingWriter {
    private val file: MutableList<String> = mutableListOf()

    override fun write(line: String) = file.add(line)
    override fun read() = file
}