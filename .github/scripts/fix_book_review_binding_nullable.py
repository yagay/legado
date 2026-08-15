from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt')
text = path.read_text(encoding='utf-8')
old = '        bookReviewEntry.bind(book, viewModel.bookSource)\n'
new = '        bookReviewEntry?.bind(book, viewModel.bookSource)\n'
if new in text:
    print('nullable book review binding already fixed')
elif old not in text:
    raise SystemExit('book review binding marker not found')
else:
    path.write_text(text.replace(old, new, 1), encoding='utf-8')
    print('fixed nullable BookReviewEntryView binding')
