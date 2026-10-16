package ro.noteorganizer.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A note's web page printed to a PDF on the phone, offline: the system's
 * own WebView lays the page out as a browser prints it, and each A4 page
 * of it goes into Android's PdfDocument as a picture -- sharp, but a
 * picture: the words cannot be selected in the PDF. Nothing is fetched
 * from anywhere.
 *
 * Why a PDF at all: sent as a web page, the note arrived as its code --
 * opened on a computer, Gmail showed the page's HTML, not the page -- and
 * Gmail on the phone would not open it. A PDF opens in the mail app.
 */
object PdfPrint {

    /** A4, in the PDF's points, and the paper's margin on every side: half an inch. */
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 36
    private const val AREA_W = PAGE_W - 2 * MARGIN
    private const val AREA_H = PAGE_H - 2 * MARGIN

    /** The printed area's width at 96 px an inch, the width a browser lays a page out at to print it: 12 pt text stays 12 pt. */
    private const val CSS_W = AREA_W * 96 / 72

    /** More pages than this is not a note to mail; the PDF stops there. */
    private const val MAX_PAGES = 40

    /** Each page's picture: sharp to read and to print at 200 dots an inch, readable at 96. */
    private const val DPI = 200
    private const val MIN_DPI = 96

    /** What the pages' pictures may take together while the PDF is written, in bytes. */
    private const val BUDGET = 64.0 * 1024 * 1024

    private var wholeDocument = false

    /** [html] printed to [out]. False when it could not be: the caller says so. */
    suspend fun print(context: Context, html: String, out: File): Boolean = suspendCancellableCoroutine { done ->
        // Before the first WebView is made, or a WebView draws only the part
        // of a page a screen would show.
        if (!wholeDocument) {
            WebView.enableSlowWholeDocumentDraw()
            wholeDocument = true
        }
        val web = WebView(context)
        web.settings.javaScriptEnabled = false
        web.settings.blockNetworkLoads = true
        web.settings.allowFileAccess = false
        // The page at its own width, one CSS pixel to a density's worth of
        // the view's: no zooming out to fit, which laid the first try out a
        // few dozen pixels wide, one letter to a line in a table's cells.
        web.settings.useWideViewPort = false
        web.settings.loadWithOverviewMode = false
        web.settings.textZoom = 100
        web.setBackgroundColor(Color.WHITE)
        // No scroll bar drawn down the page's edge: in the PDF, and across
        // every row the page breaks look for a gap in.
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        val density = context.resources.displayMetrics.density
        val width = (CSS_W * density).toInt()
        fun layOut(height: Int) {
            web.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            web.layout(0, 0, width, height)
        }
        var finished = false
        fun finish(ok: Boolean) {
            if (finished) return
            finished = true
            web.destroy()
            if (done.isActive) done.resume(ok)
        }
        // A real page's size from the start: laid out with no height, the
        // page took no width either -- and kept: a document is never shorter
        // than its window, so a short note measures one page and prints as one.
        layOut((width * AREA_H / AREA_W.toFloat()).toInt())
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val main = Handler(Looper.getMainLooper())
                // The page's height, read until it holds still -- never at a
                // time picked in advance: shrunk to a pixel and read 300 ms
                // later, it was read before the page had laid itself out
                // again, once, and the PDF came out one blank page. The
                // window stays a page tall, so a short note is one page.
                val onePage = (width * AREA_H / AREA_W.toFloat()).toInt()
                var last = -1
                var tries = 0
                fun measure() {
                    val now = (web.contentHeight * density).toInt()
                    tries++
                    if ((now <= 0 || now != last) && tries < 30) {
                        last = now
                        main.postDelayed({ measure() }, 100)
                        return
                    }
                    val height = maxOf(now, onePage)
                    layOut(height)
                    // Written once the page has drawn: a page that drew
                    // nothing gets a second chance half a second later, and
                    // after that the person is told, not sent a blank page.
                    fun attempt(left: Int) {
                        val ok = try {
                            write(web, width, height, out)
                        } catch (e: Exception) {
                            false
                        } catch (e: OutOfMemoryError) {
                            false
                        }
                        if (!ok && left > 0) main.postDelayed({ attempt(left - 1) }, 500) else finish(ok)
                    }
                    main.postDelayed({ attempt(1) }, 150)
                }
                main.postDelayed({ measure() }, 100)
            }
        }
        done.invokeOnCancellation { Handler(Looper.getMainLooper()).post { finish(false) } }
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    /**
     * Each page drawn on a picture of its own and that put on the page. A
     * WebView drawn into a PDF arrives as a picture anyway -- its words are
     * not the PDF's text -- and drawn straight onto every page, the WHOLE
     * document's picture went into each, cut to the page.
     *
     * A picture of its own, kept until the file is written: Android's PDF
     * records each page and writes the pictures only at the end, so one
     * picture painted again for every page came out as the same page four
     * times. The pictures together are kept under [BUDGET]: a long note is
     * printed at fewer dots an inch, never fewer than [MIN_DPI].
     */
    private fun write(web: WebView, width: Int, height: Int, out: File): Boolean {
        val scale = AREA_W.toFloat() / width
        val pageHeight = AREA_H / scale
        // Null when the page drew nothing at all: a note always has its
        // title, so nothing drawn is a page not ready, and a blank PDF is
        // never sent in its place.
        val starts = pageStarts(web, width, height, pageHeight) ?: return false
        val dpi = dpiFor(starts.size)
        val dotsW = AREA_W * dpi / 72
        val dotsH = AREA_H * dpi / 72
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val pictures = ArrayList<Bitmap>()
        val doc = PdfDocument()
        try {
            for ((i, top) in starts.withIndex()) {
                val bottom = starts.getOrElse(i + 1) { height.toFloat() }
                val picture = Bitmap.createBitmap(dotsW, dotsH, Bitmap.Config.ARGB_8888).also { pictures.add(it) }
                Canvas(picture).apply {
                    drawColor(Color.WHITE)
                    scale(dotsW.toFloat() / width, dotsW.toFloat() / width)
                    translate(0f, -top)
                    clipRect(0f, top, width.toFloat(), bottom)
                    web.draw(this)
                }
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, i + 1).create())
                page.canvas.drawBitmap(picture, null, Rect(MARGIN, MARGIN, PAGE_W - MARGIN, PAGE_H - MARGIN), paint)
                doc.finishPage(page)
            }
            out.outputStream().use { doc.writeTo(it) }
            return true
        } finally {
            doc.close()
            pictures.forEach { it.recycle() }
        }
    }

    /** The dots an inch [pages] pictures can have together under [BUDGET] bytes: [DPI] at most, [MIN_DPI] at least. */
    internal fun dpiFor(pages: Int): Int {
        val perInch = 72.0 * sqrt(BUDGET / (pages.coerceAtLeast(1) * 4.0 * AREA_W * AREA_H))
        return perInch.toInt().coerceIn(MIN_DPI, DPI)
    }

    /**
     * Where each page begins. A page cut at its full height goes through
     * whatever line of text is there; so each cut is moved up, by at most a
     * fifth of a page, to the nearest row of the page with nothing drawn on
     * it -- the gap between two lines -- found in a small drawing of the
     * whole page. A table taller than that gap is cut where the page ends.
     * Null when that drawing holds nothing at all.
     */
    private fun pageStarts(web: WebView, width: Int, height: Int, pageHeight: Float): List<Float>? {
        val probe = min(0.25f, sqrt(6_000_000f / (width.toFloat() * height)))
        val w = (width * probe).toInt().coerceAtLeast(1)
        val h = (height * probe).toInt().coerceAtLeast(1)
        val drawing = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            Canvas(drawing).apply { drawColor(Color.WHITE); scale(probe, probe) }.also { web.draw(it) }
            // The rightmost columns are not looked at: whatever the view
            // draws at its edge must not hide every gap.
            val looked = (w * 0.97f).toInt().coerceAtLeast(1)
            val row = IntArray(looked)
            fun blank(y: Int): Boolean {
                if (y !in 0 until h) return false
                drawing.getPixels(row, 0, looked, 0, y, looked, 1)
                return row.all { it == Color.WHITE }
            }
            if ((0 until h).all { blank(it) }) return null
            val starts = mutableListOf(0f)
            var top = 0f
            // A pixel's slack: a page's height in the page's pixels is not a whole number.
            while (top + pageHeight < height - 1 && starts.size < MAX_PAGES) {
                val ideal = top + pageHeight
                var cut = ideal
                var y = (ideal * probe).toInt()
                val lowest = ((top + pageHeight * 0.8f) * probe).toInt()
                while (y > lowest) {
                    if (blank(y)) {
                        // The middle of the gap, not its edge: the small
                        // drawing is a quarter the size, and a cut at the
                        // gap's last blank row went through the letters'
                        // tails on the full page.
                        var first = y
                        while (first - 1 > lowest && blank(first - 1)) first--
                        cut = (first + y + 1) / 2f / probe
                        break
                    }
                    y--
                }
                starts.add(cut)
                top = cut
            }
            return starts
        } finally {
            drawing.recycle()
        }
    }
}
