package uk.co.endofhome.skrooge.categories

import uk.co.endofhome.skrooge.statements.CategoryMapping
import java.io.File

interface MappingWriter {
    fun write(mapping: CategoryMapping): Boolean
    fun read(): List<CategoryMapping>
}

class FileSystemMappingWriter : MappingWriter {
    private val categoryMappingsFileOutputPath = "category-mappings/category-mappings.csv"
    override fun write(mapping: CategoryMapping): Boolean {
        return try {
            File(categoryMappingsFileOutputPath).appendText(
                "${mapping.merchant},${mapping.mainCategory},${mapping.subCategory}\n"
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun read(): List<CategoryMapping> = File(categoryMappingsFileOutputPath).readLines().map {
        val (purchase, mainCategory, subCategory) = it.split(",")
        CategoryMapping(purchase, mainCategory, subCategory)
    }
}

class StubbedMappingWriter(val file: MutableList<CategoryMapping> = mutableListOf()) : MappingWriter {
    override fun write(mapping: CategoryMapping) = file.add(mapping)
    override fun read() = file
}