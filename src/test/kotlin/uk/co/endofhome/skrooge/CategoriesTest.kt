package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.SubCategory

class CategoriesTest {
    private val categoryMappings = emptyList<String>().toMutableList()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)

    @Test
    fun `can get first category from category-schema file`() {
        assertThat(categories.subCategories()[0].category.title, equalTo("In your home"))
    }

    @Test
    fun `can get last category from category-schema file`() {
        assertThat(categories.subCategories().last().category.title, equalTo("Refurbishments"))
    }

    @Test
    fun `can get subcategories from category-schema file`() {
        val subcategories: List<SubCategory> = categories.all()[Category("Fun")]!!
        assertThat(subcategories[0].name, equalTo("Bob fun budget"))
        assertThat(subcategories[1].name, equalTo("Bill fun budget"))
    }
}