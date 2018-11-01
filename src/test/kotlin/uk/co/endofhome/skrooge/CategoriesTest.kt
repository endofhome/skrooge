package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import uk.co.endofhome.skrooge.categories.Categories

class CategoriesTest {
    private val categoryMappings = emptyList<String>().toMutableList()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)

    @Test
    fun `can get first category from category-schema file`() {
        assertThat(categories.all()[0].title, equalTo("In your home"))
    }

    @Test
    fun `can get last category from category-schema file`() {
        assertThat(categories.all().last().title, equalTo("Refurbishments"))
    }

    @Test
    fun `can get subcategories from category-schema file`() {
        val subcategories = categories.get("Fun").subcategories
        assertThat(subcategories[0].name, equalTo("Bob fun budget"))
        assertThat(subcategories[1].name, equalTo("Bill fun budget"))
    }
}