package edu.msrit.canteen.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrimaryCanteenTest {
    private fun seller(id: String, main: Boolean = false) =
        Seller(id, id, "", null, OrderingCapabilities(true, true), emptySet(), isMainCanteen = main)

    @Test fun singleCanteenIsChosen() =
        assertEquals("only", primaryCanteenId(listOf(seller("only"))))

    @Test fun mainCanteenWinsWhenSeveralExist() =
        assertEquals("main", primaryCanteenId(listOf(seller("a"), seller("main", main = true), seller("b"))))

    @Test fun firstCanteenIsFallbackAndEmptyGivesNull() {
        assertEquals("a", primaryCanteenId(listOf(seller("a"), seller("b"))))
        assertNull(primaryCanteenId(emptyList()))
    }
}
