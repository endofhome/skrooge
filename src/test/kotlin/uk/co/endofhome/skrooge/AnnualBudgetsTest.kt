package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import uk.co.endofhome.skrooge.categories.AnnualBudget
import uk.co.endofhome.skrooge.categories.AnnualBudgets
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.SubCategory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month

class AnnualBudgetsTest {
    private val category = Category("Category")
    private val subCategory = SubCategory("Subcategory", category)

    @Test
    fun `Retrieves budget for a given category, subcategory and date`() {
        val wrongSubCategory = SubCategory("Wrong SubCategory", category)
        val budgets = listOf(
                AnnualBudget(LocalDate.of(2017, Month.JANUARY, 1),
                        listOf(subCategory to BigDecimal(1.99))),
                AnnualBudget(LocalDate.of(2018, Month.JANUARY, 1),
                        listOf(wrongSubCategory to BigDecimal(5.00), subCategory to BigDecimal(24.99))),
                AnnualBudget(LocalDate.of(2019, Month.JANUARY, 1),
                        listOf(subCategory to BigDecimal(99.99)))
        )
        val annualBudgets = AnnualBudgets(budgets)

        val budgetForSubcategoryOnDate = annualBudgets.valueFor(
            subCategory,
            LocalDate.of(2018, Month.DECEMBER, 31)
        )

        assertThat(budgetForSubcategoryOnDate, equalTo(BigDecimal(24.99)))
    }

    @Test(expected = IllegalStateException::class)
    fun `Blows up if annual budget isn't available for the period`() {
        val budgets = listOf(
                AnnualBudget(LocalDate.of(2017, Month.JANUARY, 1),
                        listOf(subCategory to BigDecimal(1.99))),
                AnnualBudget(LocalDate.of(2018, Month.JANUARY, 2),
                        listOf(subCategory to BigDecimal(1.99)))
        )
        val annualBudgets = AnnualBudgets(budgets)

        annualBudgets.valueFor(subCategory, LocalDate.of(2018, Month.JANUARY, 1))
    }
}