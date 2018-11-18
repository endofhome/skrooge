package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import uk.co.endofhome.skrooge.categories.AnnualBudget
import uk.co.endofhome.skrooge.categories.AnnualBudgets
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.SubCategory
import java.time.LocalDate
import java.time.Month

class AnnualBudgetsTest {

    @Test
    fun `Retrieves budget for a given category, subcategory and date`() {
        val category = Category("Category")
        val subCategory = SubCategory("Subcategory", category)
        val wrongSubCategory = SubCategory("Wrong SubCategory", category)
        val budgets = listOf(
                AnnualBudget(LocalDate.of(2017, Month.JANUARY, 1),
                        listOf(subCategory to 1.99)),
                AnnualBudget(LocalDate.of(2018, Month.JANUARY, 1),
                        listOf(wrongSubCategory to 50.0, subCategory to 24.99)),
                AnnualBudget(LocalDate.of(2019, Month.JANUARY, 1),
                        listOf(subCategory to 99.99))
        )
        val annualBudgets = AnnualBudgets(budgets)

        val budgetForSubcategoryOnDate = annualBudgets.valueFor(
            subCategory,
            LocalDate.of(2018, Month.DECEMBER, 31)
        )

        assertThat(budgetForSubcategoryOnDate, equalTo(24.99))
    }

    @Test(expected = IllegalStateException::class)
    fun `Blows up if annual budget isn't available for the period`() {
        val category = Category("Subcategory")
        val subCategory = SubCategory("Category", category)
        val budgets = listOf(
                AnnualBudget(LocalDate.of(2017, Month.JANUARY, 1),
                        listOf(subCategory to 1.99)),
                AnnualBudget(LocalDate.of(2018, Month.JANUARY, 2),
                        listOf(subCategory to 1.99))
        )
        val annualBudgets = AnnualBudgets(budgets)

        annualBudgets.valueFor(subCategory, LocalDate.of(2018, Month.JANUARY, 1))
    }
}