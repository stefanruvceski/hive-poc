package app.stride.books.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLibraryParseTest {
    @Test fun isbnOrText() {
        assertEquals("9781847941831", OpenLibraryParse.isbn("978-1-84794-183-1"))
        assertEquals("080442957X", OpenLibraryParse.isbn("0-8044-2957-x"))
        assertNull(OpenLibraryParse.isbn("Four Thousand Weeks"))
        assertNull(OpenLibraryParse.isbn("1984"))
        assertTrue(OpenLibraryParse.searchUrl("978-1-84794-183-1").contains("isbn=9781847941831"))
        assertTrue(OpenLibraryParse.searchUrl("four thousand weeks").contains("q=four+thousand+weeks"))
    }

    @Test fun parsesDocs() {
        val body = """{"numFound":2,"docs":[
            {"title":"Four Thousand Weeks","author_name":["Oliver Burkeman"],"first_publish_year":2021,"number_of_pages_median":296,"cover_i":12345,"isbn":["1847924018","9781847924018"]},
            {"title":"No cover"},
            {"author_name":["No title"]}
        ]}"""
        val r = OpenLibraryParse.parse(body)
        assertEquals(2, r.size)
        assertEquals(FoundBook("Four Thousand Weeks", "Oliver Burkeman", 2021, 296, "https://covers.openlibrary.org/b/id/12345-M.jpg", "9781847924018"), r[0])
        assertEquals(FoundBook("No cover", "", null, null, null, null), r[1])
        assertEquals(emptyList<FoundBook>(), OpenLibraryParse.parse("not json"))
    }
}
