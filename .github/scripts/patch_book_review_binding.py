from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt')
text = path.read_text(encoding='utf-8')
old = '''        tvLasted.text = getString(R.string.lasted_show, book.latestChapterTitle)\n        showBookIntro(book)\n'''
new = '''        tvLasted.text = getString(R.string.lasted_show, book.latestChapterTitle)\n        bookReviewEntry.bind(book, viewModel.bookSource)\n        showBookIntro(book)\n'''
if new in text:
    print('book review binding already present')
elif old not in text:
    raise SystemExit('target showBook marker not found')
else:
    path.write_text(text.replace(old, new, 1), encoding='utf-8')
    print('patched BookInfoActivity showBook binding')
