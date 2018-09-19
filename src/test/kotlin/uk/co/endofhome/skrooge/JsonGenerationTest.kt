package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.should.shouldMatch
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.hamkrest.hasStatus
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month.DECEMBER
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY
import java.time.Month.OCTOBER
import java.time.Year

class JsonGenerationTest {

    @Rule
    @JvmField val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin")

    private val categoryMappings = mutableListOf<String>()
    private val testDir = "src/test/resources/"
    private val categories = Categories("${testDir}test-schema.json", categoryMappings)
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val skrooge = Skrooge(
        categories = categories,
        decisionReaderWriter = decisionReaderWriter,
        budgetDirectory = Paths.get("${testDir}budgets/")
    ).routes()

    // TODO app already works for multiple files, but some tests would be nice
    // TODO to guard against regressions.
    // TODO also, possibly all subcategories should be available, but with 0 values for actual expenditure.
    
    @Before
    fun setup() {
        val statementData = StatementData(Year.of(2017), OCTOBER, "Milford", listOf(File("doesn't matter")))
        val decisions: List<Decision> = emptyList()
        decisionReaderWriter.write(statementData, decisions)
    }

    @Test
    fun `POST to generate - json endpoint with no monthly data returns BAD REQUEST`() {
        val request = Request(GET, "/monthly-report/json").query("year", "2006").query("month", "10")

        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to generate - json endpoint with one decision in one monthly decisions file returns correct JSON`() {
        val categoryTitle = "In your home"
        val subCategories = categories.subcategoriesFor(categoryTitle)
        val decision = Decision(Line(LocalDate.of(2017, 10, 24), "B Dradley Painter and Decorator", 250.00), Category(categoryTitle, subCategories), subCategories.find { it.name == "Building insurance" })
        val statementData = StatementData(Year.of(2017), OCTOBER, "Tom", emptyList())
        decisionReaderWriter.write(statementData, listOf(decision))
        val request = Request(GET, "/monthly-report/json").query("year", "2017").query("month", "10")

        val response = skrooge(request)

        response shouldMatch hasStatus(OK)
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint with two decisions of same category in one monthly decisions file returns correct JSON`() {
        val categoryTitle = "In your home"
        val subCategories = categories.subcategoriesFor(categoryTitle)
        val decision1 = Decision(Line(LocalDate.of(2017, 10, 24), "B Dradley Painter and Decorator", 250.00), Category(categoryTitle, subCategories), subCategories.find { it.name == "Building insurance" })
        val decision2 = Decision(Line(LocalDate.of(2017, 10, 14), "OIS Removals", 500.00), Category(categoryTitle, subCategories), subCategories.find { it.name == "Building insurance" })
        val statementData = StatementData(Year.of(2017), OCTOBER, "Tom", emptyList())
        decisionReaderWriter.write(statementData, listOf(decision1, decision2))
        val request = Request(GET, "/monthly-report/json").query("year", "2017").query("month", "10")

        val response = skrooge(request)

        response shouldMatch hasStatus(OK)
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint with two decisions of different subcategory in one monthly decisions file returns correct JSON`() {
        val categoryTitle = "In your home"
        val subCategories = categories.subcategoriesFor(categoryTitle)
        val decision1 = Decision(Line(LocalDate.of(2017, 10, 24), "B Dradley Painter and Decorator", 250.00), Category(categoryTitle, subCategories), subCategories.find { it.name == "Building insurance" })
        val decision2 = Decision(Line(LocalDate.of(2017, 10, 10), "Some Bank", 300.00), Category(categoryTitle, subCategories), subCategories.find { it.name == "Mortgage" })
        val statementData = StatementData(Year.of(2017), OCTOBER, "Tom", emptyList())
        decisionReaderWriter.write(statementData, listOf(decision1, decision2))
        val request = Request(GET, "/monthly-report/json").query("year", "2017").query("month", "10")

        val response = skrooge(request)

        response shouldMatch hasStatus(OK)
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint with three decisions, two categories and two subcategories in one monthly decisions file returns correct JSON`() {
        val inYourHome = "In your home"
        val eatsAndDrinks = "Eats and drinks"
        val subCategoriesInYourHome = categories.subcategoriesFor(inYourHome)
        val subCategoriesEatsAndDrinks = categories.subcategoriesFor(eatsAndDrinks)
        val decision1 = Decision(Line(LocalDate.of(2017, 10, 24), "B Dradley Painter and Decorator", 200.00), Category(inYourHome, subCategoriesInYourHome), subCategoriesInYourHome.find { it.name == "Building insurance" })
        val decision2 = Decision(Line(LocalDate.of(2017, 10, 10), "Some Bank", 100.00), Category(inYourHome, subCategoriesInYourHome), subCategoriesInYourHome.find { it.name == "Mortgage" })
        val decision3 = Decision(Line(LocalDate.of(2017, 10, 17), "Something in a totally different category", 400.00), Category(eatsAndDrinks, subCategoriesEatsAndDrinks), subCategoriesEatsAndDrinks.find { it.name == "Food" })
        val statementData = StatementData(Year.of(2017), OCTOBER, "Tom", emptyList())
        decisionReaderWriter.write(statementData, listOf(decision1, decision2, decision3))
        val request = Request(GET, "/monthly-report/json").query("year", "2017").query("month", "10")

        val response = skrooge(request)

        response shouldMatch hasStatus(OK)
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint with decisions in different months returns correct JSON`() {
        val subCategoriesInYourHome = categories.subcategoriesFor("In your home")
        val decision1 = Decision(Line(LocalDate.of(2017, 1, 24), "B Dradley Painter and Decorator", 1.00), Category("In your home", subCategoriesInYourHome), subCategoriesInYourHome.find { it.name == "Building insurance" })
        val decision2 = Decision(Line(LocalDate.of(2017, 2, 10), "Some Bank", 2.00), Category("In your home", subCategoriesInYourHome), subCategoriesInYourHome.find { it.name == "Mortgage" })
        val septemberStatementData = StatementData(Year.of(2017), JANUARY, "Tom", emptyList())
        val octoberStatementData = StatementData(Year.of(2017), FEBRUARY, "Tom", emptyList())
        decisionReaderWriter.write(septemberStatementData, listOf(decision1))
        decisionReaderWriter.write(octoberStatementData, listOf(decision2))
        val request = Request(GET, "/monthly-report/json").query("year", "2017").query("month", "2")

        val response = skrooge(request)

        response shouldMatch hasStatus(OK)
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint for final month in the year shows correct budget total`() {
        val subCategoriesInYourHome = categories.subcategoriesFor("In your home")
        val decision1 = Decision(Line(LocalDate.of(2017, 12, 24), "B Dradley Painter and Decorator", 1.00), Category("In your home", subCategoriesInYourHome), subCategoriesInYourHome.find { it.name == "Building insurance" })
        val decemberStatementData = StatementData(Year.of(2017), DECEMBER, "Tom", emptyList())
        decisionReaderWriter.write(decemberStatementData, listOf(decision1))
        val request = Request(GET, "/monthly-report/json").query("year", "2017").query("month", "12")

        val response = skrooge(request)

        response shouldMatch hasStatus(OK)
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint with decisions in different months and mismatched statements returns correct JSON`() {
        val localDecisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories, Paths.get("src/test/resources/decisions"))
        val midMonthBudgetDirectory = Paths.get("${testDir}budgets/mid-month/")
        val localSkrooge = Skrooge(
                categories = categories,
                decisionReaderWriter = localDecisionReaderWriter,
                budgetDirectory = midMonthBudgetDirectory
        ).routes()

        val subCategoriesInYourHome = categories.subcategoriesFor("In your home")
        val decision1 = Decision(Line(LocalDate.of(2016, 12, 15), "B Dradley Painter and Decorator", 1.00), Category("In your home", subCategoriesInYourHome), subCategoriesInYourHome.find { it.name == "Building insurance" })
        val decision2 = Decision(Line(LocalDate.of(2017, 2, 14), "Some Bank", 2.00), Category("In your home", subCategoriesInYourHome), subCategoriesInYourHome.find { it.name == "Mortgage" })
        val januaryStatementData = StatementData(Year.of(2017), JANUARY, "Tom", listOf(File("2017_January_SomeBank.csv")))
        val februaryStatementData = StatementData(Year.of(2017), FEBRUARY, "Tom", listOf(File("2017_February_SomeBank.csv")))
        localDecisionReaderWriter.write(januaryStatementData, listOf(decision1))
        localDecisionReaderWriter.write(februaryStatementData, listOf(decision2))
        val request = Request(GET, "/monthly-report/json").query("year", "2017").query("month", "2")

        val response = localSkrooge(request)

        response shouldMatch hasStatus(OK)
        approver.assertApproved(response.bodyString())
    }
}
