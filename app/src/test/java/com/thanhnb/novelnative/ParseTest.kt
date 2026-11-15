package com.thanhnb.novelnative

import org.junit.Assert.assertEquals
import org.junit.Test

class ParseTest {
    @Test fun geminiModelsFallBackToWorkerDefaults() {
        val m = parseGeminiModels(" gemini-a 10/1000/50 , gemini-b 1/x/2 ,, gemini-c")
        assertEquals(listOf("gemini-a", "gemini-b", "gemini-c"), m.map { it.name })
        assertEquals(listOf(10, 1000, 50), m[0].let { listOf(it.rpm, it.tpm, it.rpd) })
        assertEquals(listOf(5, 250000, 20), m[1].let { listOf(it.rpm, it.tpm, it.rpd) }) // trần hỏng → mặc định như worker
        assertEquals(listOf(5, 250000, 20), m[2].let { listOf(it.rpm, it.tpm, it.rpd) })
    }

    @Test fun releaseLinesStripsMarkdown() {
        val body = "## What's Changed\n- Sửa **nhảy chương** sớm\n* `Detail` cuộn được\n\n### Ghi chú\n**Full Changelog**: x"
        assertEquals(listOf("• Sửa nhảy chương sớm", "• Detail cuộn được", "Ghi chú"), releaseLines(body))
    }

    @Test fun friendlyErrors() {
        assertEquals("Không kết nối được máy chủ — kiểm tra mạng", friendly(java.net.SocketTimeoutException()))
        assertEquals("Lỗi không rõ", friendly(null))
    }
}
