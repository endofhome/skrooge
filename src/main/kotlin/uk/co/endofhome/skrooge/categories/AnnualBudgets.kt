package uk.co.endofhome.skrooge.categories

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.SubCategory
import java.nio.file.Path
import java.time.LocalDate
import java.time.Month

class AnnualBudgets(private val budgets: List<AnnualBudget>) {

    companion object {
        fun from(budgetDirectory: Path): AnnualBudgets {
            val budgetsFromFiles = budgetDirectory.toFile()
                .listFiles()
                .filter { it.name.endsWith(".json") }
                .map { file ->
                    val (year, month, day) = file.name.split('/')
                        .last()
                        .substringBefore('.')
                        .split('-')
                        .drop(1)
                        .map { it.toInt() }
                    AnnualBudget.from(
                        LocalDate.of(
                            year,
                            Month.of(month),
                            day
                        ),
                        file.readText()
                    )
                }

            return AnnualBudgets(budgetsFromFiles)
        }
    }

    fun valueFor(category: Category, subCategory: SubCategory, date: LocalDate): Double {
        val budgetDataForCategory = budgetFor(date).budgetData.find {
            it.first.first == subCategory && it.first.second.title == category.title
        } ?: throw IllegalStateException("Subcategory ${subCategory.name} not available in budget for $date.")
        return budgetDataForCategory.second
    }

    fun budgetFor(date: LocalDate): AnnualBudget =
        budgets.find { annualBudget ->
            date >= annualBudget.startDateInclusive &&
                date < annualBudget.startDateInclusive.plusYears(1)
        } ?: throw IllegalStateException("Budget unavailable for period starting $date")
}

data class AnnualBudget(val startDateInclusive: LocalDate, val budgetData: List<Pair<Pair<SubCategory, Category>, Double>>) {
    companion object {
        fun from(startDateInclusive: LocalDate, json: String): AnnualBudget {
            val mapper = ObjectMapper().registerModule(KotlinModule())
            val annualBudgetJson: AnnualBudgetJson = mapper.readValue(json)
            val budgetData = annualBudgetJson.categories.flatMap { categoryJson ->
                val thisCategoryJson = annualBudgetJson.categories.find(categoryJson.title)
                val category = Category(thisCategoryJson.title, thisCategoryJson.subcategories.map { SubCategory(it.name) })
                category.subcategories.map { subCategory ->
                    subCategory to category to thisCategoryJson.subcategories.find(subCategory.name).monthly_budget
                }
            }
            return AnnualBudget(startDateInclusive, budgetData)
        }
    }
}

data class AnnualBudgetJson(
    val yearStart: Int,
    val yearEnd: Int,
    val categories: List<CategoryJson>
)

data class CategoryJson(val title: String, val subcategories: List<SubCategoryBudgetJson>)
data class SubCategoryBudgetJson(val name: String, val monthly_budget: Double)

fun List<CategoryJson>.find(category: String): CategoryJson =
    this.find { it.title == category } ?: throw java.lang.IllegalStateException("Category $category not found in ${this.joinToString(",") { it.title }}")

fun List<SubCategoryBudgetJson>.find(subcategory: String): SubCategoryBudgetJson =
    this.find { it.name == subcategory } ?: throw java.lang.IllegalStateException("Subcategory $subcategory not found in ${this.joinToString(",") { it.name }}")
