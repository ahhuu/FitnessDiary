package com.cz.fitnessdiary.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FoodUnitUtilsTest {
    @Test
    public void normalizesCanonicalUnitsWithoutInventingWeight() {
        assertEquals("g", FoodUnitUtils.normalize("克"));
        assertEquals("ml", FoodUnitUtils.normalize("毫升"));
        assertEquals("个", FoodUnitUtils.normalize("粒"));
        assertEquals(FoodUnitUtils.UNKNOWN, FoodUnitUtils.normalize("unknown"));
        assertFalse(FoodUnitUtils.isSupported("unknown"));
        assertTrue(FoodUnitUtils.isMass("kg"));
        assertTrue(FoodUnitUtils.isVolume("L"));
        assertFalse(FoodUnitUtils.isReliableLibraryUnit("碗", 0));
        assertFalse(FoodUnitUtils.isReliableLibraryUnit("个", 100));
        assertTrue(FoodUnitUtils.isReliableLibraryUnit("个", 100, true));
    }
}
