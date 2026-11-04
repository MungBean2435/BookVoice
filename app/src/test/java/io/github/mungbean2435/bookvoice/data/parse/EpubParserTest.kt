package io.github.mungbean2435.bookvoice.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @Test
    fun parsesMetadataChaptersAndImages() {
        val dir = Files.createTempDirectory("epub-test").toFile()
        val epub = File(dir, "book.epub")
        buildEpub(epub, opfDir = "OEBPS", imageHref = "images/", imageFilesDir = "OEBPS/images")

        val book = EpubParser(epub, File(dir, "images")).parse("兜底书名")

        assertEquals("测试书", book.title)
        assertEquals("作者甲", book.author)
        assertEquals(2, book.chapters.size)
        assertEquals("第一章 夜", book.chapters[0].title)
        // h1 + 两段正文 = 3 个文本块
        assertEquals(3, book.chapters[0].blocks.size)
        assertEquals(1, book.chapters[0].images.size)
        assertEquals(2, book.chapters[0].images[0].afterBlockIdx)
        assertTrue(File(book.chapters[0].images[0].path).exists())
        assertNotNull(book.coverPath)
        assertTrue(File(book.coverPath!!).exists())
    }

    @Test
    fun resolvesParentRelativePaths() {
        val dir = Files.createTempDirectory("epub-test2").toFile()
        val epub = File(dir, "nested.epub")
        // OPF 在子目录里，图片用 ../ 回到上级目录
        buildEpub(epub, opfDir = "OEBPS/text", imageHref = "../images/", imageFilesDir = "OEBPS/images")

        val book = EpubParser(epub, File(dir, "images")).parse("兜底书名")

        assertEquals(2, book.chapters.size)
        assertEquals(1, book.chapters[0].images.size)
        assertTrue(File(book.chapters[0].images[0].path).exists())
    }

    @Test
    fun keepsConsecutiveImagesInSamePosition() {
        val dir = Files.createTempDirectory("epub-gallery").toFile()
        val epub = File(dir, "gallery.epub")
        buildEpub(
            epub,
            opfDir = "OEBPS",
            imageHref = "images/",
            imageFilesDir = "OEBPS/images",
            galleryCount = 4
        )

        val book = EpubParser(epub, File(dir, "images")).parse("兜底书名")

        val images = book.chapters[0].images
        assertEquals(5, images.size)
        // 正文第一张图在位置 2，随后 4 张连续插图都在位置 3
        assertEquals(1, images.count { it.afterBlockIdx == 2 })
        assertEquals(4, images.count { it.afterBlockIdx == 3 })
        assertEquals(5, images.map { it.path }.distinct().size)
    }

    @Test
    fun parsesProvidedRealBook() {
        val path = System.getenv("TEST_EPUB") ?: return
        val file = File(path)
        org.junit.Assume.assumeTrue(file.exists())
        val dir = Files.createTempDirectory("epub-real").toFile()

        val started = System.currentTimeMillis()
        val book = EpubParser(file, File(dir, "images")).parse("未命名")
        val elapsed = System.currentTimeMillis() - started

        println("real book: title=${book.title} chapters=${book.chapters.size} images=${book.chapters.sumOf { it.images.size }} blocks=${book.chapters.sumOf { it.blocks.size }} parseMs=$elapsed")
        assertTrue(book.chapters.isNotEmpty())
        assertTrue(book.chapters.sumOf { it.blocks.size } > 0)
    }

    private fun buildEpub(
        file: File,
        opfDir: String,
        imageHref: String,
        imageFilesDir: String,
        galleryCount: Int = 0
    ) {
        val opf = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="bookid">urn:uuid:test</dc:identifier>
                <dc:title>测试书</dc:title>
                <dc:creator>作者甲</dc:creator>
                <dc:language>zh-CN</dc:language>
                <meta name="cover" content="cover-img"/>
              </metadata>
              <manifest>
                <item id="cover-img" href="${imageHref}cover.png" media-type="image/png" properties="cover-image"/>
                <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                <item id="fig" href="${imageHref}figure.png" media-type="image/png"/>
              </manifest>
              <spine>
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
        """.trimIndent()

        val container = """
            <?xml version="1.0" encoding="utf-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="$opfDir/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
        """.trimIndent()

        val ch1 = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>第一章</title></head>
            <body>
              <h1>第一章 夜</h1>
              <p>夜色落在这座城市的上空。</p>
              <img src="${imageHref}figure.png" alt="插图"/>
              <p>风从江面上吹过来。</p>
              ${(1..galleryCount).joinToString("\n") { "<img src=\"${imageHref}gallery${it}.png\" alt=\"g\"/>" }}
            </body></html>
        """.trimIndent()

        val ch2 = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>第二章</title></head>
            <body><h1>第二章 旧相册</h1><p>我翻开那本旧相册。</p></body></html>
        """.trimIndent()

        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            fun putBytes(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            put("META-INF/container.xml", container)
            put("$opfDir/content.opf", opf)
            put("$opfDir/chapter1.xhtml", ch1)
            put("$opfDir/chapter2.xhtml", ch2)
            putBytes("$imageFilesDir/cover.png", byteArrayOf(1, 2, 3, 4))
            putBytes("$imageFilesDir/figure.png", byteArrayOf(5, 6, 7, 8))
            (1..galleryCount).forEach { putBytes("$imageFilesDir/gallery$it.png", byteArrayOf(9, 10)) }
        }
    }

    @Test
    fun readsVolumeNamesFromEpub3Nav() {
        val dir = Files.createTempDirectory("epub-nav").toFile()
        val epub = File(dir, "nav.epub")
        buildEpubWithToc(epub, useNcx = false)

        val book = EpubParser(epub, File(dir, "images")).parse("兜底书名")

        assertEquals(2, book.chapters.size)
        assertEquals("第一章 夜", book.chapters[0].title)
        assertEquals("第一卷 起风", book.chapters[0].volume)
        assertEquals("第二卷 归途", book.chapters[1].volume)
    }

    @Test
    fun readsVolumeNamesFromEpub2Ncx() {
        val dir = Files.createTempDirectory("epub-ncx").toFile()
        val epub = File(dir, "ncx.epub")
        buildEpubWithToc(epub, useNcx = true)

        val book = EpubParser(epub, File(dir, "images")).parse("兜底书名")

        assertEquals(2, book.chapters.size)
        assertEquals("第一卷 起风", book.chapters[0].volume)
        assertEquals("第二卷 归途", book.chapters[1].volume)
    }

    private fun buildEpubWithToc(file: File, useNcx: Boolean) {
        val navItem = if (useNcx) {
            """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""
        } else {
            """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>"""
        }
        val spineAttrs = if (useNcx) " toc=\"ncx\"" else ""
        val spineNav = if (useNcx) "" else """<itemref idref="nav"/>"""
        val opf = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="bookid">urn:uuid:toc</dc:identifier>
                <dc:title>分卷书</dc:title>
                <dc:creator>作者乙</dc:creator>
              </metadata>
              <manifest>
                $navItem
                <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine$spineAttrs>
                $spineNav
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
        """.trimIndent()

        val container = """
            <?xml version="1.0" encoding="utf-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
        """.trimIndent()

        val ch1 = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>第一章</title></head>
            <body><h1>第一章 夜</h1><p>夜色落在江面上。</p></body></html>
        """.trimIndent()
        val ch2 = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>第二章</title></head>
            <body><h1>第二章 旧相册</h1><p>我翻开旧相册。</p></body></html>
        """.trimIndent()
        val nav = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <head><title>目录</title></head>
            <body>
              <nav epub:type="toc">
                <ol>
                  <li><a href="chapter1.xhtml">第一卷 起风</a>
                    <ol>
                      <li><a href="chapter1.xhtml">第一章 夜</a></li>
                      <li><a href="chapter2.xhtml">第二章 旧相册</a></li>
                    </ol>
                  </li>
                  <li><a href="chapter2.xhtml">第二卷 归途</a>
                    <ol>
                      <li><a href="chapter2.xhtml">第二章 旧相册</a></li>
                    </ol>
                  </li>
                </ol>
              </nav>
            </body></html>
        """.trimIndent()
        val ncx = """
            <?xml version="1.0" encoding="utf-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="v1">
                  <navLabel><text>第一卷 起风</text></navLabel>
                  <content src="chapter1.xhtml"/>
                  <navPoint id="c1">
                    <navLabel><text>第一章 夜</text></navLabel>
                    <content src="chapter1.xhtml"/>
                  </navPoint>
                  <navPoint id="c2">
                    <navLabel><text>第二章 旧相册</text></navLabel>
                    <content src="chapter2.xhtml"/>
                  </navPoint>
                </navPoint>
                <navPoint id="v2">
                  <navLabel><text>第二卷 归途</text></navLabel>
                  <content src="chapter2.xhtml"/>
                  <navPoint id="c3">
                    <navLabel><text>第二章 旧相册</text></navLabel>
                    <content src="chapter2.xhtml"/>
                  </navPoint>
                </navPoint>
              </navMap>
            </ncx>
        """.trimIndent()

        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", container)
            put("OEBPS/content.opf", opf)
            put("OEBPS/chapter1.xhtml", ch1)
            put("OEBPS/chapter2.xhtml", ch2)
            if (useNcx) put("OEBPS/toc.ncx", ncx) else put("OEBPS/nav.xhtml", nav)
        }
    }
}
