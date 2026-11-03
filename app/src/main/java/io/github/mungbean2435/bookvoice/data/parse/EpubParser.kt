package io.github.mungbean2435.bookvoice.data.parse

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * EPUB 解析：zip → container.xml → OPF → spine 顺序读 XHTML。
 * 只取纯文本块和插图，不还原 CSS 排版。
 */
class EpubParser(private val file: File, private val imagesDir: File) {

    private var zip: ZipFile? = null

    fun parse(fallbackTitle: String): ParsedBook {
        imagesDir.mkdirs()
        val z = ZipFile(file)
        zip = z
        try {
            val containerEntry = z.getEntry("META-INF/container.xml")
                ?: throw IOException("不是有效的 EPUB：缺少 META-INF/container.xml")
            val containerDoc = Jsoup.parse(
                z.getInputStream(containerEntry).use { it.readBytes() }.toString(Charsets.UTF_8),
                "", Parser.xmlParser()
            )
            val opfPath = containerDoc.getElementsByTag("rootfile").firstOrNull()
                ?.attr("full-path")
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("EPUB 缺少 OPF 路径")

            val opfEntry = findEntry(opfPath) ?: throw IOException("EPUB 缺少 OPF 文件：$opfPath")
            val opfDoc = Jsoup.parse(
                zipBytes(opfEntry).toString(Charsets.UTF_8),
                "", Parser.xmlParser()
            )
            val baseDir = opfPath.substringBeforeLast('/', "")

            val manifest = HashMap<String, String>()
            opfDoc.getElementsByTag("item").forEach { el ->
                val id = el.attr("id")
                val href = el.attr("href")
                if (id.isNotEmpty() && href.isNotEmpty()) manifest[id] = href
            }
            val spineIds = opfDoc.getElementsByTag("itemref")
                .mapNotNull { it.attr("idref").takeIf(String::isNotEmpty) }

            if (spineIds.isEmpty()) throw IOException("EPUB 没有可读的 spine（正文顺序）")

            val bookTitle = firstText(opfDoc, "dc:title") ?: fallbackTitle
            val author = firstText(opfDoc, "dc:creator")
            val coverPath = extractCover(opfDoc, manifest, baseDir)

            val chapters = ArrayList<ParsedChapter>()
            for (idref in spineIds) {
                val href = manifest[idref] ?: continue
                if (!isHtml(href)) continue
                val path = resolve(baseDir, href)
                val entry = findEntry(path) ?: findEntry(href) ?: continue
                val doc = Jsoup.parse(zipBytes(entry).toString(Charsets.UTF_8), "", Parser.htmlParser())
                val chapterDir = path.substringBeforeLast('/', "")
                val (blocks, images) = collectBlocks(doc, chapterDir)
                if (blocks.isEmpty() && images.isEmpty()) continue
                chapters.add(ParsedChapter(chapterTitle(doc, chapters.size), blocks, images))
            }

            if (chapters.isEmpty()) throw IOException("EPUB 里没有解析到正文")

            return ParsedBook(
                title = bookTitle,
                author = author,
                coverPath = coverPath,
                chapters = chapters
            )
        } finally {
            zip = null
            z.close()
        }
    }

    // ---------------- 正文与插图 ----------------

    private fun collectBlocks(doc: Document, chapterDir: String): Pair<List<String>, List<ParsedImage>> {
        val blocks = ArrayList<String>()
        val images = ArrayList<ParsedImage>()
        val seen = HashSet<String>()
        val body = doc.body() ?: return blocks to images
        walk(body, chapterDir, blocks, images, seen)
        return blocks to images
    }

    private fun walk(
        el: Element,
        chapterDir: String,
        blocks: MutableList<String>,
        images: MutableList<ParsedImage>,
        seen: MutableSet<String>
    ) {
        for (child in el.children()) {
            val tag = child.tagName().lowercase()
            when {
                tag == "script" || tag == "style" || tag == "head" -> Unit
                tag == "img" || tag == "image" -> addImage(child, chapterDir, blocks.size, images, seen)
                else -> {
                    val hasBlockChild = child.children().any { it.tagName().lowercase() in BLOCK_TAGS }
                    if (tag in BLOCK_TAGS && !hasBlockChild) {
                        val text = child.text().trim()
                        if (text.isNotEmpty()) blocks.addAll(TextSplit.splitLong(text))
                        child.getElementsByTag("img").forEach {
                            addImage(it, chapterDir, blocks.size, images, seen)
                        }
                    } else {
                        walk(child, chapterDir, blocks, images, seen)
                    }
                }
            }
        }
    }

    private fun addImage(
        img: Element,
        chapterDir: String,
        afterBlockIdx: Int,
        images: MutableList<ParsedImage>,
        seen: MutableSet<String>
    ) {
        val src = listOf("src", "xlink:href", "data-src")
            .map { img.attr(it) }
            .firstOrNull { it.isNotBlank() }
            ?: return
        val path = resolve(chapterDir, src)
        if (!seen.add(path)) return
        val entry = findEntry(path) ?: findEntry(src) ?: return
        val ext = entry.name.substringAfterLast('.', "").lowercase()
        if (ext !in IMAGE_EXTS) return
        val out = File(imagesDir, "img_${images.size}.$ext")
        runCatching {
            out.writeBytes(zipBytes(entry))
            images.add(ParsedImage(afterBlockIdx, out.absolutePath))
        }
    }

    // ---------------- 元数据 ----------------

    private fun firstText(doc: Document, tag: String): String? =
        doc.getElementsByTag(tag)
            .map { it.text().trim() }
            .firstOrNull { it.isNotEmpty() }

    private fun chapterTitle(doc: Document, index: Int): String {
        val heading = doc.selectFirst("h1, h2, h3")?.text()?.trim()
        if (!heading.isNullOrBlank() && heading.length <= 60) return heading
        val title = doc.title().trim()
        if (title.isNotEmpty()) return title
        return "第 ${index + 1} 章"
    }

    private fun extractCover(doc: Document, manifest: Map<String, String>, baseDir: String): String? {
        val byMeta = doc.getElementsByTag("meta")
            .firstOrNull { it.attr("name").equals("cover", ignoreCase = true) && it.attr("content").isNotEmpty() }
            ?.attr("content")
            ?.let { manifest[it] }
        val href = byMeta
            ?: doc.getElementsByTag("item").firstOrNull { it.attr("properties").contains("cover-image") }?.attr("href")
            ?: manifest.values.firstOrNull {
                it.contains("cover", ignoreCase = true) &&
                    it.substringAfterLast('.', "").lowercase() in IMAGE_EXTS
            }
            ?: return null
        val entry = findEntry(resolve(baseDir, href)) ?: return null
        val ext = entry.name.substringAfterLast('.', "").lowercase()
        if (ext !in IMAGE_EXTS) return null
        val out = File(imagesDir, "cover.$ext")
        return runCatching {
            out.writeBytes(zipBytes(entry))
            out.absolutePath
        }.getOrNull()
    }

    // ---------------- zip 工具 ----------------

    private fun zipBytes(entry: ZipEntry): ByteArray {
        val z = zip ?: throw IOException("EPUB 已关闭")
        return z.getInputStream(entry).use { it.readBytes() }
    }

    private fun findEntry(path: String): ZipEntry? {
        val z = zip ?: return null
        val candidates = listOf(
            path,
            path.removePrefix("/"),
            decodeSafe(path),
            decodeSafe(path.removePrefix("/"))
        )
        for (candidate in candidates) {
            z.getEntry(candidate)?.let { return it }
        }
        return null
    }

    private fun decodeSafe(value: String): String = runCatching {
        URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)

    private fun resolve(baseDir: String, href: String): String {
        val clean = href.substringBefore('#').substringBefore('?')
        val full = if (baseDir.isEmpty()) clean else "$baseDir/$clean"
        val parts = ArrayList<String>()
        for (segment in full.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    private fun isHtml(href: String): Boolean {
        val lower = href.lowercase().substringBefore('#')
        return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
    }

    companion object {
        private val BLOCK_TAGS = setOf(
            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6",
            "li", "blockquote", "td", "th", "pre", "dd", "dt", "figcaption", "section", "article"
        )
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }
}
