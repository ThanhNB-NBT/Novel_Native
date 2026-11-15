package com.thanhnb.novelnative.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

const val PACKAGE = "com.thanhnb.novelnative"

/**
 * Hành trình chỉ ĐỌC: app chạy trên server prod nên tuyệt đối không mở chương (ghi tiến độ + xếp hàng dịch),
 * không vào Tu Tiên (gọi RPC tu luyện). Tủ truyện → Khám phá (cuộn, mở Detail) → Lịch âm.
 */
fun MacrobenchmarkScope.browse() {
    device.wait(Until.hasObject(By.text("Khám phá")), 15_000)
    tab("Khám phá")
    device.waitForIdle()
    scrollMain()
    // Detail chỉ GET. Nhấn thẻ truyện ("702 chương"), KHÔNG nhấn "Đọc ngay" ở banner (mở chương)
    device.findObject(By.textEndsWith(" chương"))?.let {
        it.click()
        device.waitForIdle()
        scrollMain()
        device.pressBack()
        device.waitForIdle()
    }
    tab("Lịch âm")
    scrollMain()
    tab("Tủ truyện")
}

private fun MacrobenchmarkScope.tab(name: String) {
    device.findObject(By.text(name))?.click()
    device.wait(Until.hasObject(By.text(name)), 3_000)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.scrollMain() {
    // Vuốt theo toạ độ: Compose dựng lại nút cuộn (bìa thu lên) nên UiObject2.fling hay dính StaleObjectException
    val x = device.displayWidth / 2
    val (top, bottom) = device.displayHeight * 3 / 10 to device.displayHeight * 7 / 10
    repeat(2) { device.swipe(x, bottom, x, top, 8); device.waitForIdle() }
    device.swipe(x, top, x, bottom, 8)
    device.waitForIdle()
}
