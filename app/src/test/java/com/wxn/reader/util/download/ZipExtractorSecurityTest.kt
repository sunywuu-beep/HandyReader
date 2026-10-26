package com.wxn.reader.util.download

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 验证 ZipExtractor 安全解压逻辑：
 * 1. 正常 zip 条目安全解压至目标目录；
 * 2. 包含路径穿越（Zip Slip, 包含 ../ 逃逸目标目录）的恶意条目必须被拦截并抛出 SecurityException。
 */
class ZipExtractorSecurityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val extractor = ZipExtractor()

    @Test
    fun normalZipExtractsSuccessfully() = runBlocking {
        val zipFile = tmp.newFile("valid.zip")
        val targetDir = tmp.newFolder("target_normal")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("hello.txt"))
            zos.write("hello world".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("sub/nested.txt"))
            zos.write("nested content".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = extractor.extract(zipFile.absolutePath, targetDir.absolutePath)
        assertTrue("正常 zip 应解压成功", result.isSuccess)
        assertTrue("解压后文件应存在", File(targetDir, "hello.txt").exists())
        assertTrue("嵌套文件应存在", File(targetDir, "sub/nested.txt").exists())
    }

    @Test
    fun zipSlipEntryIsBlocked() = runBlocking {
        val zipFile = tmp.newFile("malicious.zip")
        val targetDir = tmp.newFolder("target_sandbox")

        // 创建包含 ../ 逃逸目标目录的恶意 zip
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("../evil.txt"))
            zos.write("malicious payload".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = extractor.extract(zipFile.absolutePath, targetDir.absolutePath)
        assertTrue("包含路径穿越的 zip 提取应失败", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(
            "应抛出 SecurityException，实际抛出: ${exception?.javaClass?.name}",
            exception is SecurityException
        )
        assertFalse(
            "逃逸文件不应出现在沙箱外部目录",
            File(targetDir.parentFile, "evil.txt").exists()
        )
    }
}
