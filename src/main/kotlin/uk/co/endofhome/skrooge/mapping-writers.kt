package uk.co.endofhome.skrooge

import java.io.File

interface MappingWriter {
    fun write(line: String): Boolean
    fun read(): List<String>
}

class FileSystemMappingWriter : MappingWriter {
    private val categoryMappingsFileOutputPath = "category-mappings/category-mappings.csv"
    override fun write(line: String): Boolean {
        return try {
            File(categoryMappingsFileOutputPath).appendText(line + "\n")
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun read(): List<String> = File(categoryMappingsFileOutputPath).readLines()
}

class StubbedMappingWriter(val file: MutableList<String> = mutableListOf()) : MappingWriter {
    override fun write(line: String) = file.add(line)
    override fun read() = file
}