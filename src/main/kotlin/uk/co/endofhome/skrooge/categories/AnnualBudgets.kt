package uk.co.endofhome.skrooge.categories

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.SubCategory
import java.io.File
import java.nio.file.Path
import java.time.LocalDate

class AnnualBudgets(private val budgets: List<AnnualBudget>) {

    companion object {
        fun from(budgetDirectory: Path): AnnualBudgets {
            val budgetsFromFiles = budgetDirectory.toFile()
                .listFiles()
                .filter { it.name.endsWith(".json") }
                .map { file ->
                    val (year, month, day) = file.parseDateIntsFromFilename()
                    AnnualBudget.from(LocalDate.of(year, month, day), file.readText())
                }

            return AnnualBudgets(budgetsFromFiles)
        }

        private fun File.parseDateIntsFromFilename(): List<Int> =
            this.name.split('/')
                .last()
                .substringBefore('.')
                .split('-')
                .drop(1)
                .map { element -> element.toInt() }
    }

    fun valueFor(subCategory: SubCategory, date: LocalDate): Double {
        val budgetDataForSubCategory = budgetFor(date).budgetData.find { subcategoryBudget ->
            subcategoryBudget.first == subCategory
        } ?: throw IllegalStateException("Subcategory ${subCategory.name} not available in budget for $date.")
        return budgetDataForSubCategory.second
    }

    fun budgetFor(date: LocalDate): AnnualBudget =
        budgets.find { annualBudget ->
            date >= annualBudget.startDateInclusive &&
                date < annualBudget.startDateInclusive.plusYears(1)
        } ?: throw IllegalStateException("Budget unavailable for period starting $date")
}

data class AnnualBudget(val startDateInclusive: LocalDate, val budgetData: List<Pair<SubCategory, Double>>) {
    companion object {
        fun from(startDateInclusive: LocalDate, json: String): AnnualBudget {
            val mapper = ObjectMapper().registerModule(KotlinModule())
            val annualBudgetJson: AnnualBudgetJson = mapper.readValue(json)
            val budgetData = annualBudgetJson.categories.flatMap { categoryJson ->
                categoryJson.subcategories.map { subcategoryJson ->
                    SubCategory(subcategoryJson.name, Category(categoryJson.title)) to subcategoryJson.monthly_budget
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
