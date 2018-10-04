package uk.co.endofhome.skrooge.categories

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.SubCategory
import java.io.File

class Categories(private val schemaFilePath: String = "category-schema/category-schema.json", val categoryMappings: MutableList<String> = File("category-mappings/category-mappings.csv").readLines().toMutableList()) {

    fun all(): List<Category> {
        val schemaFile = File(schemaFilePath)
        val contents: String = schemaFile.readText()
        val mapper = ObjectMapper().registerModule(KotlinModule())
        val categoriesData: CategoriesData = mapper.readValue(contents)
        return categoriesData.toList()
    }

    fun withSelection(subCategory: SubCategory?): CategoriesWithSelection {
        val titles = all().map { it.title }
        val subCategories: List<List<SubCategoryWithSelection>> = all().map { cat ->
            cat.subcategories.map { subCat ->
                SubCategoryWithSelection(subCat, selectedString(subCat, subCategory))
            }
        }
        val catsWithSelection = titles.zip(subCategories).map { CategoryWithSelection(it.first, it.second) }
        return CategoriesWithSelection(catsWithSelection)
    }

    fun subcategoriesFor(category: String): List<SubCategory> {
        return all().filter { it.title == category }.flatMap { it.subcategories }
    }

    private fun selectedString(subCategory: SubCategory, anotherSubCategory: SubCategory?): String {
        return when (subCategory == anotherSubCategory) {
            true -> " selected"
            false -> ""
        }
    }

    data class CategoriesData(val categories: List<Category>) {
        fun toList() = categories
    }
}

data class CategoryWithSelection(val title: String, val subCategories: List<SubCategoryWithSelection>)
data class CategoriesWithSelection(val categories: List<CategoryWithSelection>)
data class SubCategoryWithSelection(val subCategory: SubCategory, val selector: String)