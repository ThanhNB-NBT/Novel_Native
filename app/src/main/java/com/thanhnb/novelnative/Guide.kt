package com.thanhnb.novelnative

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private class GuideItem(val icon: ImageVector, val title: String, val sub: String, val steps: List<String>, val tip: String? = null)

private val sections = listOf(
    GuideItem(
        Icons.Rounded.Swipe, "Làm quen với 4 tab", "Bố cục chính của app",
        listOf(
            "Thanh dưới cùng có 4 tab: Tủ truyện · Khám phá · Thông báo · Tôi.",
            "Bấm vào tab để chuyển; tab đang chọn mở rộng ra kèm tên.",
            "Đăng nhập ở tab Tôi (hoặc khi app yêu cầu) để dùng tủ sách, theo dõi truyện và yêu cầu dịch.",
        ),
    ),
    GuideItem(
        Icons.Rounded.Explore, "Khám phá & tìm truyện", "Duyệt kho, tìm kiếm, lọc thể loại",
        listOf(
            "Tab Khám phá hiện các mục: mới cập nhật, đề cử, theo thể loại… Bấm \"Xem tất cả\" để mở cả mục.",
            "Bấm icon kính lúp để TÌM KIẾM — gõ tên tiếng Việt (không cần dấu) hoặc tiếng Trung đều được.",
            "Trong màn tìm kiếm có bộ LỌC theo thể loại, trạng thái và nguồn.",
        ),
    ),
    GuideItem(
        Icons.Rounded.MenuBook, "Trang truyện & yêu cầu dịch", "Xem thông tin, mục lục, bắt đầu dịch",
        listOf(
            "Trang truyện hiện bìa, tóm tắt, thể loại, trạng thái và mục lục chương.",
            "Truyện mới thường CHƯA dịch sẵn — bấm \"Đọc truyện\" hoặc nút \"Dịch\" ở danh sách chương để xếp hàng dịch (+50, +100 chương hoặc tới chương bạn chọn).",
            "Thẻ \"Thuật ngữ truyện\" mở bảng tên riêng — duyệt, sửa, xoá và vá lại các chương đã dịch.",
            "Nút \"Theo dõi\" đưa truyện vào Tủ truyện; truyện đang đọc dở cũng tự vào đó.",
        ),
    ),
    GuideItem(
        Icons.Rounded.ChromeReaderMode, "Đọc truyện", "Giao diện đọc, chuyển chương, tuỳ chỉnh",
        listOf(
            "Mở CÀI ĐẶT ĐỌC (icon Aa) để đổi font, cỡ chữ, giãn dòng, màu nền, chế độ CUỘN hay LẬT TRANG — có khung xem trước.",
            "Chế độ lật trang: vuốt ngang hoặc dùng phím âm lượng (bật trong cài đặt đọc). Chế độ cuộn có TỰ CUỘN với tốc độ tuỳ chỉnh.",
            "Đọc hết chương, vuốt thêm một lần để sang chương kế.",
        ),
        tip = "Muốn NGHE truyện: menu ⋮ → Nghe truyện. App đọc thành tiếng, tô đoạn đang đọc, có hẹn giờ tắt và nghe được khi tắt màn hình.",
    ),
    GuideItem(
        Icons.Rounded.EditNote, "Sửa chỗ dịch sai", "Góp ý bản dịch ngay trong màn đọc",
        listOf(
            "Thấy từ dịch sai? NHẤN GIỮ đoạn văn đó trong trang đọc.",
            "Bôi chọn từ cần sửa rồi chọn \"Thay từ\" (tên riêng được học vào thuật ngữ), \"Lưu cả đoạn\" hoặc \"Gợi ý AI\".",
            "Bản sửa hiện ngay trong chương; sửa tên riêng thì có thể vá cho các chương khác trong Thuật ngữ truyện.",
        ),
    ),
    GuideItem(
        Icons.Rounded.Bookmarks, "Tủ truyện & tiến độ đọc", "Theo dõi truyện, đọc tiếp, chương mới",
        listOf(
            "Tab Tủ truyện liệt kê truyện bạn theo dõi kèm thanh tiến độ; chạm vào dòng truyện để ĐỌC TIẾP đúng chương đang dở.",
            "Tab Thông báo hiện chương mới dịch xong của truyện trong tủ (7 ngày gần nhất).",
            "Menu ⋮ trên mỗi truyện: xem thông tin hoặc xoá khỏi tủ.",
        ),
    ),
    GuideItem(
        Icons.Rounded.HourglassBottom, "Hàng đợi dịch", "Xem tiến độ dịch của cả hệ thống",
        listOf(
            "Vào Tôi → Hàng đợi dịch: thấy truyện đang dịch, số chương chờ và tốc độ chương/giờ.",
            "Chạm một truyện để xem từng chương: đang dịch, đang tải về hay chờ dịch.",
            "Danh sách tự làm mới mỗi 15 giây.",
        ),
    ),
    GuideItem(
        Icons.Rounded.DownloadDone, "Đọc offline", "Tải truyện về máy để đọc không cần mạng",
        listOf(
            "Ở trang truyện bấm thẻ \"Đọc offline\" để tải các chương đã dịch về máy.",
            "Mất mạng vẫn mở truyện, mục lục và đọc chương đã tải; có mạng lại thì chương được cập nhật nếu bản dịch đổi.",
            "Quản lý, cập nhật hoặc xoá bản tải ở Tủ truyện → icon tải xuống góc phải.",
        ),
    ),
)

@Composable
fun GuideScreen() {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Mẹo đọc, lưu và yêu cầu dịch", "Hướng dẫn", "引", back = true)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "App đọc truyện Trung dịch máy sang tiếng Việt. Mỗi mục dưới đây là một tính năng — bấm để xem các bước dùng.",
                    Modifier.padding(4.dp, 4.dp, 4.dp, 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(sections) { GuideCard(it) }
        }
    }
}

@Composable
private fun GuideCard(s: GuideItem) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    PaperCard(Modifier.animateContentSize(), onClick = { open = !open }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer.copy(.55f)), Alignment.Center) {
                Icon(s.icon, null, Modifier.size(22.dp), cs.primary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium)
                Text(s.sub, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(if (open) 180f else 0f), cs.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.steps.forEachIndexed { i, t ->
                    Row {
                        Text("${i + 1}", Modifier.width(24.dp), style = monoStyle(13, cs.primary), fontWeight = FontWeight.Bold)
                        Text(t, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                s.tip?.let {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer.copy(.4f)).padding(12.dp)) {
                        Icon(Icons.Rounded.Lightbulb, null, Modifier.size(18.dp), cs.primary)
                        Text(it, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
