package com.eventoscelebrativos.service;

public record PersonMinistryShadowReadComparisonOptions(
        boolean compareOrder,
        boolean comparePageMetadata
) {

    public static PersonMinistryShadowReadComparisonOptions deterministicPage() {
        return new PersonMinistryShadowReadComparisonOptions(true, true);
    }

    public static PersonMinistryShadowReadComparisonOptions unorderedList() {
        return new PersonMinistryShadowReadComparisonOptions(false, false);
    }
}
