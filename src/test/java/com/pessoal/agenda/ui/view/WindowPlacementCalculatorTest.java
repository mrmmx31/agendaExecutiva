package com.pessoal.agenda.ui.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowPlacementCalculatorTest {
    private static final double MARGIN = 24;
    private static final double EPSILON = 0.0001;

    @Test
    void centersSmallerWindowOnOwner() {
        var screen = new WindowPlacementCalculator.Bounds(0, 0, 1920, 1040);
        var owner = new WindowPlacementCalculator.Bounds(100, 80, 1200, 800);
        var window = new WindowPlacementCalculator.WindowSpec(0, 0, 860, 540, 760, 500);

        var result = WindowPlacementCalculator.fit(screen, owner, window, MARGIN, false);

        assertEquals(270, result.x(), EPSILON);
        assertEquals(210, result.y(), EPSILON);
        assertEquals(860, result.width(), EPSILON);
        assertEquals(540, result.height(), EPSILON);
        assertInside(screen, result);
    }

    @Test
    void shrinksWindowAndMinimumsWhenTheyExceedScreen() {
        var screen = new WindowPlacementCalculator.Bounds(0, 0, 1280, 720);
        var owner = new WindowPlacementCalculator.Bounds(0, 0, 1280, 720);
        var window = new WindowPlacementCalculator.WindowSpec(0, 0, 1600, 900, 1400, 800);

        var result = WindowPlacementCalculator.fit(screen, owner, window, MARGIN, false);

        assertEquals(24, result.x(), EPSILON);
        assertEquals(24, result.y(), EPSILON);
        assertEquals(1232, result.width(), EPSILON);
        assertEquals(672, result.height(), EPSILON);
        assertEquals(result.width(), result.minWidth(), EPSILON);
        assertEquals(result.height(), result.minHeight(), EPSILON);
        assertInside(screen, result);
    }

    @Test
    void clampsCenterWhenOwnerIsPartiallyOutsideScreen() {
        var screen = new WindowPlacementCalculator.Bounds(0, 0, 1280, 720);
        var owner = new WindowPlacementCalculator.Bounds(1100, 650, 500, 300);
        var window = new WindowPlacementCalculator.WindowSpec(0, 0, 600, 400, 320, 240);

        var result = WindowPlacementCalculator.fit(screen, owner, window, MARGIN, false);

        assertEquals(656, result.x(), EPSILON);
        assertEquals(296, result.y(), EPSILON);
        assertInside(screen, result);
    }

    @Test
    void expandsSmallWindowToConfiguredMinimums() {
        var screen = new WindowPlacementCalculator.Bounds(0, 0, 1920, 1080);
        var window = new WindowPlacementCalculator.WindowSpec(0, 0, 300, 200, 700, 500);

        var result = WindowPlacementCalculator.fit(screen, null, window, MARGIN, false);

        assertEquals(700, result.width(), EPSILON);
        assertEquals(500, result.height(), EPSILON);
        assertEquals(610, result.x(), EPSILON);
        assertEquals(290, result.y(), EPSILON);
        assertInside(screen, result);
    }

    @Test
    void preservesCompactWindowPositionButKeepsItVisible() {
        var screen = new WindowPlacementCalculator.Bounds(0, 0, 1280, 720);
        var window = new WindowPlacementCalculator.WindowSpec(-300, 1000, 170, 165, 0, 0);

        var result = WindowPlacementCalculator.fit(screen, null, window, MARGIN, true);

        assertEquals(24, result.x(), EPSILON);
        assertEquals(531, result.y(), EPSILON);
        assertEquals(170, result.width(), EPSILON);
        assertEquals(165, result.height(), EPSILON);
        assertInside(screen, result);
    }

    private static void assertInside(WindowPlacementCalculator.Bounds screen,
                                     WindowPlacementCalculator.Placement placement) {
        assertTrue(placement.x() >= screen.x());
        assertTrue(placement.y() >= screen.y());
        assertTrue(placement.x() + placement.width() <= screen.x() + screen.width() + EPSILON);
        assertTrue(placement.y() + placement.height() <= screen.y() + screen.height() + EPSILON);
    }
}
