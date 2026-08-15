from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt')
text = path.read_text()

old_import = 'import io.legado.app.enhance.review.ReviewContext\n'
new_import = 'import io.legado.app.enhance.review.LegacyParagraphReviewResolver\nimport io.legado.app.enhance.review.ReviewContext\n'
if old_import not in text:
    raise SystemExit('review import target not found')
text = text.replace(old_import, new_import, 1)

old = '''        val rule = if (source.isJsSource()) {
            null
        } else {
            source.ruleReview ?: run {
                toastOnUi(R.string.review_rule_missing)
                return
            }
        }
'''
new = '''        val rule = if (source.isJsSource()) {
            null
        } else {
            source.ruleReview
                ?: LegacyParagraphReviewResolver.resolve(source, reviewData)
                ?: run {
                    toastOnUi(R.string.review_rule_missing)
                    return
                }
        }
'''
if old not in text:
    raise SystemExit('review rule target not found')
text = text.replace(old, new, 1)
path.write_text(text)
