package uk.co.endofhome.skrooge

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

class CategoryHelpers(private val schemaFilePath: String = "category-schema/category-schema.json", val categoryMappings: MutableList<String> = File("category-mappings/category-mappings.csv").readLines().toMutableList()) {

    fun categories(): List<Category> {
        val schemaFile = File(schemaFilePath)
        val contents: String = schemaFile.readText()
        val mapper = ObjectMapper().registerModule(KotlinModule())
        val categories: Categories = mapper.readValue(contents)
        return categories.toList()
    }

    fun categoriesWithSelection(subCategory: SubCategory?): CategoriesWithSelection {
        val titles = categories().map { it.title }
        val subCategories: List<List<SubCategoryWithSelection>> = categories().map { cat ->
            cat.subcategories.map { subCat ->
                SubCategoryWithSelection(subCat, selectedString(subCat, subCategory))
            }
        }
        val catsWithSelection = titles.zip(subCategories).map { CategoryWithSelection(it.first, it.second) }
        return CategoriesWithSelection(catsWithSelection)
    }

    fun subcategoriesFor(category: String): List<SubCategory> {
        return categories().filter { it.title == category }.flatMap { it.subcategories }
    }

    private fun selectedString(subCategory: SubCategory, anotherSubCategory: SubCategory?): String {
        return when (subCategory == anotherSubCategory) {
            true -> " selected"
            false -> ""
        }
    }

    data class Categories(val categories: List<Category>) {
        fun toList() = categories
    }
}