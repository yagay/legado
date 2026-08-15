package io.legado.app.enhance.review

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule

/**
 * Resolves review capabilities from explicit ReviewRule first, then from conservative legacy
 * protocol adapters. Keep UI components unaware of individual source/platform quirks.
 */
internal object ReviewCapabilityResolver {

    fun resolveBookReview(source: BookSource?, book: Book): ReviewRule? {
        source ?: return null
        return source.ruleReview?.takeIf(::isBookReviewRule)
            ?: LegacyBookReviewResolver.resolve(source, book)
    }

    private fun isBookReviewRule(rule: ReviewRule): Boolean {
        return rule.enabled &&
            !rule.reviewDetailUrl.isNullOrBlank() &&
            !rule.detailListRule.isNullOrBlank() &&
            !rule.detailContentRule.isNullOrBlank() &&
            rule.summaryParagraphIndexRule.isNullOrBlank()
    }
}
