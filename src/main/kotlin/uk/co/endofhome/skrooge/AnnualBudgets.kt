package uk.co.endofhome.skrooge

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Path
import java.time.LocalDate
import java.time.Month

class AnnualBudgets(private val budgets: List<AnnualBudget>) {

    companion object {
        fun from(budgetDirectory: Path): AnnualBudgets {
            val budgetsFromFiles = budgetDirectory.toFile().listFiles().map { file ->
                val dateInts = file.name
                                            .split('/')
                                            .last()
                                            .substringBefore('.')
                                            .split('-')
                                            .drop(1)
                                            .map { it.toInt() }
                AnnualBudget.from(
                    LocalDate.of(dateInts[0], Month.of(dateInts[1]), dateInts[2]),
                    file.readText()
                )
            }

            return AnnualBudgets(budgetsFromFiles)
        }
    }

    fun valueFor(category: Category, subCategory: SubCategory, date: LocalDate): Double {
        val budget = budgetFor(date)
        return if (budget != null) {
            val budgetDataForCategory = budget.budgetData.find {
                it.first.first == subCategory &&
                it.first.second.title == category.title
            }
            budgetDataForCategory?.second ?: error("Subcategory ${subCategory.name} not available in this budget.")
        } else {
            error("Budget for this period unavailable")
        }
    }

    private fun budgetFor(date: LocalDate): AnnualBudget? =
        budgets.find { annualBudget ->
            date >= annualBudget.startDateInclusive &&
            date < annualBudget.startDateInclusive.plusYears(1)
        }
}

data class AnnualBudget(val startDateInclusive: LocalDate, val budgetData: List<Pair<Pair<SubCategory, Category>, Double>>) {
    companion object {
        fun from(startDateInclusive: LocalDate, json: String): AnnualBudget {
            val mapper = ObjectMapper().registerModule(KotlinModule())
            val annualBudgetJson: AnnualBudgetJson = mapper.readValue(json)
            val budgetData = annualBudgetJson.categories.flatMap { categoryJson ->
                val thisCategoryJson = annualBudgetJson.categories.find { it.title == categoryJson.title }!!
                val category = Category(thisCategoryJson.title, thisCategoryJson.subcategories.map { SubCategory(it.name) })
                category.subcategories.map {subCategory ->
                    subCategory to category to thisCategoryJson.subcategories.find { it.name == subCategory.name }!!.monthly_budget
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