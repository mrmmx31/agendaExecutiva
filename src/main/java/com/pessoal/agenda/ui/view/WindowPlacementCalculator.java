package com.pessoal.agenda.ui.view;

final class WindowPlacementCalculator {

    record Bounds(double x, double y, double width, double height) {
        Bounds {
            if (!Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(width) || !Double.isFinite(height)
                    || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Screen bounds must be finite and positive.");
            }
        }
    }

    record WindowSpec(double x, double y, double width, double height,
                      double minWidth, double minHeight) {}

    record Placement(double x, double y, double width, double height,
                     double minWidth, double minHeight) {}

    private WindowPlacementCalculator() {}

    static Placement fit(Bounds screen, Bounds owner, WindowSpec window,
                         double requestedMargin, boolean preservePlacement) {
        double margin = finiteNonNegative(requestedMargin);
        double marginX = Math.min(margin, Math.max(0, (screen.width() - 1) / 2));
        double marginY = Math.min(margin, Math.max(0, (screen.height() - 1) / 2));
        double availableWidth = screen.width() - marginX * 2;
        double availableHeight = screen.height() - marginY * 2;

        double minWidth = Math.min(finiteNonNegative(window.minWidth()), availableWidth);
        double minHeight = Math.min(finiteNonNegative(window.minHeight()), availableHeight);
        double width = clamp(finitePositive(window.width()), minWidth, availableWidth);
        double height = clamp(finitePositive(window.height()), minHeight, availableHeight);

        double desiredX;
        double desiredY;
        if (preservePlacement) {
            desiredX = finiteOr(window.x(), screen.x() + marginX);
            desiredY = finiteOr(window.y(), screen.y() + marginY);
        } else if (owner != null) {
            desiredX = owner.x() + (owner.width() - width) / 2;
            desiredY = owner.y() + (owner.height() - height) / 2;
        } else {
            desiredX = screen.x() + (screen.width() - width) / 2;
            desiredY = screen.y() + (screen.height() - height) / 2;
        }

        double minX = screen.x() + marginX;
        double minY = screen.y() + marginY;
        double maxX = screen.x() + screen.width() - marginX - width;
        double maxY = screen.y() + screen.height() - marginY - height;
        return new Placement(
                clamp(desiredX, minX, maxX),
                clamp(desiredY, minY, maxY),
                width, height, minWidth, minHeight);
    }

    private static double finitePositive(double value) {
        return Double.isFinite(value) && value > 0 ? value : 1;
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }
}
