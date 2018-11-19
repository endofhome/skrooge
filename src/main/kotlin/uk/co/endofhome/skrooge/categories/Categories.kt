package uk.co.endofhome.skrooge.categories

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.SubCategory
import java.io.File

class Categories(private val schemaFilePath: String = "category-schema/category-schema.json", val categoryMappings: MutableList<String> = File("category-mappings/category-mappings.csv").readLines().toMutableList()) {

    fun all(): Map<Category, List<SubCategory>> = subCategories().groupBy { it.category }

    fun subCategories(): List<SubCategory> {
        val schemaFile = File(schemaFilePath)
        val contents: String = schemaFile.readText()
        val mapper = ObjectMapper().registerModule(KotlinModule())
        val categoriesData: CategoriesData = mapper.readValue(contents)
        return categoriesData.toList().flatMap { categoryJson ->
            categoryJson.subcategories.map {
                SubCategory(it.name, Category(categoryJson.title))
            }
        }
    }

    fun get(categoryName: String, subCategoryName: String): SubCategory =
        subCategories().find { it.category.title == categoryName && it.name == subCategoryName}
            ?: throw IllegalStateException("Category: '$categoryName', subcategory: '$subCategoryName' not found in schema file.")

    fun withSelection(subCategory: SubCategory?): CategoriesWithSelection {
        val categoryTitles = all().keys.map { it.title }
        val subCategories: List<List<SubCategoryWithSelection>> = subCategories().map { subCat ->
            SubCategoryWithSelection(subCat, selectedString(subCat, subCategory))
        }.groupBy { it.subCategory.category }.values.toList()
        val catsWithSelection = categoryTitles.zip(subCategories).map { CategoryWithSelection(it.first, it.second) }
        return CategoriesWithSelection(catsWithSelection)
    }

    private fun selectedString(subCategory: SubCategory, anotherSubCategory: SubCategory?): String {
        return when (subCategory == anotherSubCategory) {
            true -> " selected"
            false -> ""
        }
    }

    data class CategoriesData(val categories: List<CategoryJson>) {
        fun toList() = categories
    }
}

data class CategoryWithSelection(val title: String, val subCategories: List<SubCategoryWithSelection>)
data class CategoriesWithSelection(val categories: List<CategoryWithSelection>)
data class SubCategoryWithSelection(val subCategory: SubCategory, val selector: String)