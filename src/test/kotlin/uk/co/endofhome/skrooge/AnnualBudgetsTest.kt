package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import java.time.LocalDate
import java.time.Month

class AnnualBudgetsTest {

    @Test
    fun `Retrieves budget for a given category, subcategory and date`() {
        val subCategory = SubCategory("Subcategory")
        val category = Category("Category", listOf(subCategory))
        val wrongCategory = Category("Wrong Category", listOf(subCategory))
        val budgets = listOf(
            AnnualBudget(LocalDate.of(2017, Month.JANUARY, 1),
                listOf(subCategory to category to 1.99)),
            AnnualBudget(LocalDate.of(2018, Month.JANUARY, 1),
                listOf(subCategory to wrongCategory to 50.0,
                       subCategory to category to 24.99)),
            AnnualBudget(LocalDate.of(2019, Month.JANUARY, 1),
                listOf(subCategory to category to 99.99))
        )
        val annualBudgets = AnnualBudgets(budgets)

        val budgetForSubcategoryOnDate = annualBudgets.valueFor(
            category,
            subCategory,
            LocalDate.of(2018, Month.DECEMBER, 31)
        )

        assertThat(budgetForSubcategoryOnDate, equalTo(24.99))
    }

    @Test(expected = IllegalStateException::class)
    fun `Blows up if annual budget isn't available for the period`() {
        val subCategory = SubCategory("Subcategory")
        val category = Category("Category", listOf(subCategory))
        val budgets = listOf(
            AnnualBudget(LocalDate.of(2017, Month.JANUARY, 1),
                listOf(subCategory to category to 1.99)),
            AnnualBudget(LocalDate.of(2018, Month.JANUARY, 2),
                listOf(subCategory to category to 1.99))
        )
        val annualBudgets = AnnualBudgets(budgets)

        annualBudgets.valueFor(category, subCategory, LocalDate.of(2018, Month.JANUARY, 1))
    }
}