package com.ambientcompanion.data.profile

import com.ambientcompanion.domain.screen.AppCategory
import com.ambientcompanion.domain.screen.CompanionDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryResolverTest {
    @Test fun `known packages receive safe category defaults`() {
        val resolver = AppCategoryResolver()
        assertEquals(AppCategory.VIDEO, resolver.resolve("com.google.android.youtube"))
        assertEquals(AppCategory.FINANCE, resolver.resolve("com.example.mobilebank"))
        assertEquals(CompanionDisplayMode.EDGE_PEEK, AppProfileRepository.categoryDefault("youtube", AppCategory.VIDEO).displayMode)
        assertEquals(CompanionDisplayMode.PRIVACY, AppProfileRepository.categoryDefault("bank", AppCategory.FINANCE).displayMode)
    }
}
