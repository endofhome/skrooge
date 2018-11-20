package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.OK
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.monthlyJsonReport
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.DecisionState.Decision
import uk.co.endofhome.skrooge.decisions.FileSystemDecisionReaderReaderWriter
import uk.co.endofhome.skrooge.decisions.Line
import uk.co.endofhome.skrooge.decisions.StubbedDecisionReaderWriter
import uk.co.endofhome.skrooge.decisions.SubCategory
import uk.co.endofhome.skrooge.statements.StatementMetadata
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month.DECEMBER
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY
import java.time.Month.OCTOBER
import java.time.Year

class JsonGenerationTest {

    @Rule @JvmField
    val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val categoryMappings = mutableListOf<String>()
    private val testDir = "src/test/resources/"
    private val categories = Categories("${testDir}test-schema.json", categoryMappings)
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val skrooge = Skrooge(
        categories = categories,
        decisionReaderWriter = decisionReaderWriter,
        budgetDirectory = Paths.get("${testDir}budgets/")
    ).routes

    private val inYourHome = "In your home"
    private val aCommonDecision = Decision(
        line = Line(LocalDate.of(2017, 10, 24), "B Dradley Painter and Decorator", 200.00),
        subCategory = SubCategory("Building insurance", Category(inYourHome))
    )

    @Before
    fun setup() {
        val statementMetadata = StatementMetadata(Year.of(2017), OCTOBER, "Milford", "some-bank")
        val decisions: List<Decision> = emptyList()
        decisionReaderWriter.write(statementMetadata, decisions)
    }

    @Test
    fun `POST to generate - json endpoint with no monthly data returns OK with empty JSON body`() {
        val request = Request(GET, monthlyJsonReport).query("year", "2006").query("month", "10")

        val response = skrooge(request)
        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString(), equalTo("{}"))
    }

    @Test
    fun `POST to generate - json endpoint with one decision in one monthly decisions file returns correct JSON`() {
        val statementMetadata = StatementMetadata(Year.of(2017), OCTOBER, "Tom", "some-bank")
        decisionReaderWriter.write(statementMetadata, listOf(aCommonDecision))
        val request = Request(GET, monthlyJsonReport).query("year", "2017").query("month", "10")

        val response = skrooge(request)

        assertThat(response.status, equalTo(OK))
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint with two decisions of same category in one monthly decisions file returns correct JSON`() {
        val buildingInsuranceDecision = Decision(
            line = Line(LocalDate.of(2017, 10, 14), "OIS Removals", 500.00),
            subCategory = SubCategory("Building insurance", Category(inYourHome))
        )
        val statementMetadata = StatementMetadata(Year.of(2017), OCTOBER, "Tom", "some-bank")
        decisionReaderWriter.write(statementMetadata, listOf(aCommonDecision, buildingInsuranceDecision))
        val request = Request(GET, monthlyJsonReport).query("year", "2017").query("month", "10")

        val response = skrooge(request)

        assertThat(skrooge(request).status, equalTo(OK))
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint with two decisions of different subcategory in one monthly decisions file returns correct JSON`() {
        val mortgageDecision = Decision(
            line = Line(LocalDate.of(2017, 10, 10), "Some Bank", 300.00),
            subCategory = SubCategory("Mortgage", Category(inYourHome))
        )
        val statementMetadata = StatementMetadata(Year.of(2017), OCTOBER, "Tom", "some-bank")
        decisionReaderWriter.write(statementMetadata, listOf(aCommonDecision, mortgageDecision))
        val request = Request(GET, monthlyJsonReport).query("year", "2017").query("month", "10")

        val response = skrooge(request)

        assertThat(skrooge(request).status, equalTo(OK))
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint with three decisions, two categories and two subcategories in one monthly decisions file returns correct JSON`() {
        val eatsAndDrinks = "Eats and drinks"
        val mortgageDecision = Decision(
            line = Line(LocalDate.of(2017, 10, 10), "Some Bank", 100.00),
            subCategory = SubCategory("Mortgage", Category(inYourHome))
        )
        val foodDecision = Decision(
            line = Line(LocalDate.of(2017, 10, 17), "Something in a totally different category", 400.00),
            subCategory = SubCategory("Food", Category(eatsAndDrinks))
        )
        val statementMetadata = StatementMetadata(Year.of(2017), OCTOBER, "Tom", "some-bank")
        decisionReaderWriter.write(statementMetadata, listOf(aCommonDecision, mortgageDecision, foodDecision))
        val request = Request(GET, monthlyJsonReport).query("year", "2017").query("month", "10")

        val response = skrooge(request)

        assertThat(skrooge(request).status, equalTo(OK))
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint with decisions in different months returns correct JSON`() {
        val buildingInsuranceDecision = Decision(
            line = Line(LocalDate.of(2017, 1, 24), "B Dradley Painter and Decorator", 1.00),
            subCategory = SubCategory("Building insurance", Category(inYourHome))
        )
        val mortgageDecision = Decision(
            line = Line(LocalDate.of(2017, 2, 10), "Some Bank", 2.00),
            subCategory = SubCategory("Mortgage", Category(inYourHome))
        )
        val septemberStatementData = StatementMetadata(Year.of(2017), JANUARY, "Tom", "some-bank")
        val octoberStatementData = StatementMetadata(Year.of(2017), FEBRUARY, "Tom", "some-bank")
        decisionReaderWriter.write(septemberStatementData, listOf(buildingInsuranceDecision))
        decisionReaderWriter.write(octoberStatementData, listOf(mortgageDecision))
        val request = Request(GET, monthlyJsonReport).query("year", "2017").query("month", "2")

        val response = skrooge(request)

        assertThat(skrooge(request).status, equalTo(OK))
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST to generate - json endpoint for final month in the year shows correct budget total`() {
        val decision = Decision(
            line = Line(LocalDate.of(2017, 12, 24), "B Dradley Painter and Decorator", 1.00),
            subCategory = SubCategory("Building insurance", Category(inYourHome))
        )
        val decemberStatementData = StatementMetadata(Year.of(2017), DECEMBER, "Tom", "some-bank")
        decisionReaderWriter.write(decemberStatementData, listOf(decision))
        val request = Request(GET, monthlyJsonReport).query("year", "2017").query("month", "12")

        val response = skrooge(request)

        assertThat(skrooge(request).status, equalTo(OK))
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
        ).routes

        val buildingInsuranceDecision = Decision(
            line = Line(LocalDate.of(2016, 12, 15), "B Dradley Painter and Decorator", 1.00),
            subCategory = SubCategory("Building insurance", Category(inYourHome))
        )
        val foodDecision = Decision(
            line = Line(LocalDate.of(2017, 2, 14), "Some Bank", 2.00),
            subCategory = SubCategory("Food", Category("Eats and drinks"))
        )

        val mealsAtWorkDecision = Decision(
            line = Line(LocalDate.of(2017, 2, 13), "Some Bank", 5.00),
            subCategory = SubCategory("Meals at work", Category("Eats and drinks"))
        )
        val januaryStatementData = StatementMetadata(Year.of(2017), JANUARY, "Tom", "SomeBank")
        val februaryStatementData = StatementMetadata(Year.of(2017), FEBRUARY, "Tom", "SomeBank")
        localDecisionReaderWriter.write(januaryStatementData, listOf(buildingInsuranceDecision))
        localDecisionReaderWriter.write(februaryStatementData, listOf(foodDecision, mealsAtWorkDecision))
        val request = Request(GET, monthlyJsonReport).query("year", "2017").query("month", "2")

        val response = localSkrooge(request)

        assertThat(response.status, equalTo(OK))
        approver.assertApproved(response.bodyString())
    }
}
