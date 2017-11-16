package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test

class CategoriesTest {
    val categories = CategoryHelpers.categories("src/test/resources/test-schema.json")

    @Test
    fun `can get first category from category-schema file`() {
        assertThat(categories[0].title, equalTo("In your home"))
    }

    @Test
    fun `can get last category from category-schema file`() {
        assertThat(categories.last().title, equalTo("Refurbishments"))
    }

    @Test
    fun `can get subcategories from category-schema file`() {
        val subcategories = categories.find { it.title == "Fun" }!!.subcategories
        assertThat(subcategories[0].name, equalTo("Bob fun budget"))
        assertThat(subcategories[1].name, equalTo("Bill fun budget"))
    }
}