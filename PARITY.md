# Đối chiếu với app Flutter (../Novel_Persona/app/lib)

Mục tiêu: giống bản Flutter, TRỪ Tu Tiên (screens/cultivation/*, cultivation.dart, tab giữa).

## Đã có (0.2.0)
- Khám phá 4 mục + Xem tất cả (trộn nguồn, xoay theo ngày), Tìm kiếm không dấu + lọc
- Chi tiết truyện, mục lục, theo dõi, đọc tiếp, dấu trang
- Màn đọc: chờ dịch (poll), tự dịch trước 15 chương, lưu tiến độ + % cuộn, cỡ chữ/giãn dòng/lề/màu/căn đều, mục lục, báo lỗi chương
- Tủ truyện, Thông báo (pull 7 ngày, xoá), Tôi (giao diện, tự dịch trước), Đăng nhập email

## Giao diện 24/10: phong cách ColorOS 17 "Living Flow" — thẻ kính bo 28dp có vệt sáng mép, nhún khi chạm (bounceClick), chuyển cảnh lò xo, tiêu đề lớn bỏ triện, dock kính nở theo tab. Muốn bỏ: git revert commit "giao diện ColorOS 17".

## Còn thiếu — theo thứ tự nên làm
1. ~~Màn đọc nâng cao — lật trang, font chữ, phím âm lượng, tự cuộn, vuốt quá mép đổi chương, gạch chân thuật ngữ, 14 font Sans + 4 Serif, 15 màu nền sáng/tối riêng, sheet có khung xem trước~~ (xong)
   - 12 font Sans thêm tải từ Google Fonts khi chọn (Fonts.kt, cache filesDir/webfonts, thiếu mạng rơi về Jakarta; chip có ↓ khi chưa tải); bỏ: quà tu tiên cuối chương
2. ~~Sửa bản dịch~~ xong (Edit.kt): nhấn giữ đoạn → bôi từ + Thay từ (edit_chapter_vi, tên riêng học vào thuật ngữ), Lưu cả đoạn (edit_chapter_para), Gợi ý AI (goi-y-dich), báo lỗi. Chưa có: gợi ý chip thuật ngữ/Hán-Việt, nút "Áp cả truyện", tra Google Dịch
3. ~~Thuật ngữ truyện~~ xong (Glossary.kt, vào từ trang chi tiết): duyệt/bỏ gợi ý LLM, thêm/sửa/xoá, chọn nhiều để xoá, vá chương cũ + thanh tiến trình, báo cáo dịch sai. Chưa có: tra Hán-Việt, văn phong dịch + quét lỗi (admin), cập nhật bản offline sau vá
4. ~~Nghe truyện (TTS)~~ xong (Tts.kt): menu ⋮ → Nghe truyện, thanh điều khiển đáy, chọn giọng + tốc độ, đọc nền (foreground service), highlight + cuộn theo đoạn, tự sang chương sau. Có cả highlight ở chế độ lật trang + hẹn giờ tắt (15–60 phút / hết chương)
5. ~~Đọc offline~~ xong (Offline.kt): thẻ Đọc offline ở trang chi tiết (tải nền theo lô, chỉ kéo chương thiếu/đã đổi theo updated_at), màn Bản offline (Tủ truyện → icon), mất mạng thì truyện/mục lục/chương/tiến độ tự rơi về bản trên máy; chương đọc online và chương vừa vá thuật ngữ tự cập nhật xuống bản offline
6. ~~Chi tiết: chọn nhiều chương để dịch lại, nút Dịch, mã truyện~~ xong (Detail.kt): nút Dịch → hộp thoại +50/+100/+200/đến hết/tới chương N; chế độ chọn nhiều chương + Dịch lại (chỉ admin, chọn trọn bộ → retranslate_all). Chưa có: báo thông báo khi chương dịch lại xong (notify_mine)
7. ~~Hồ sơ~~ xong (Profile.kt): tab Tôi hiện avatar + tên + số truyện đang đọc / chương đã đọc / chuỗi ngày; chạm thẻ → Sửa hồ sơ (tên hiển thị + 30 ảnh emoji). Bỏ: biểu tượng tab Tu Tiên
8. ~~Hàng đợi dịch~~ xong (Queue.kt, vào từ tab Tôi): tóm tắt (chương/giờ, đang dịch, đang chờ), gom theo truyện, chạm xem từng chương, mục Vừa dịch xong + Xoá, tự làm mới 15 giây
9. Hướng dẫn xong (Guide.kt, 8 mục bấm mở rộng); Có gì mới xong (Changelog.kt: 8 release GitHub gần nhất, lưu bản cuối để xem khi mất mạng) — (~~kiểm tra cập nhật~~ xong: Update.kt + release.yml, cần đặt secrets cho repo)
10. ~~Lịch âm~~ xong (Lunar.kt): thẻ hôm nay + lịch tháng (chạm dòng âm lịch ở Tôi)
11. ~~Quản trị (admin)~~ xong bản gọn (Admin*.kt, vào từ tab Tôi → Quản trị, chỉ hiện khi profiles.is_admin): 5 tab (bỏ Tu Tiên)
   - Worker (nhịp tim, số job, gộp theo truyện, sheet job: chạy lại/ưu tiên lên đầu/huỷ; tự làm mới 20s), Crawl (nhịp 24h, cấu hình dịch + crawler sửa bằng hộp thoại, đổi engine, bật/tắt nguồn, truyện mới về 24h), Đang đọc, Truyện (thống kê kho, tìm không dấu, lọc, ẩn/hiện, sửa, xoá, trang quản trị 1 truyện: chương + model/token), Báo cáo (xử lý)
   - Nút trên thanh: chạy lại job lỗi, quét lỗi dịch; menu ⋮: Thống kê API (cột 14 ngày, lượt Gemini theo key, sức khoẻ model), Theo dõi VPS (CPU/RAM/disk/mạng + lệnh khởi động lại), Nhật ký lỗi (job lỗi + client_errors). Mọi thao tác ghi đều có hộp thoại xác nhận.
   - Bỏ/chưa làm: bộ chọn model có danh mục (model_picker; sửa knob bằng ô chữ), knob `gemini_catalog`/`nvidia_catalog`, "Yêu cầu dịch" theo khoảng chương trong trang quản trị truyện, xoá truyện từ tab Worker (vẫn xoá được ở tab Truyện), sửa nhãn/IP và xoá dòng host ở màn VPS, nhật ký lỗi cục bộ của app (native chưa có AppErrorLog)
12. Giao diện: ~~theme.dart, header + triện, dock nổi, trang chủ 5 kiểu mục, chi tiết bìa mờ, màu nhấn/nền~~ (xong 0.2.0); còn skeleton, ink_transition, ambient (màu theo bìa)

## 24/10 bổ sung
- Thông báo hệ thống (Notify.kt): kiểm lúc mở app chương truyện trong tủ dịch xong sau lần trước (không có Realtime), xin quyền Android 13+, chương mình bấm Dịch lại (notify_mine) cũng báo và hiện ở tab Thông báo.
- Hán-Việt (HanViet.kt + assets/hanviet.tsv): gợi ý âm đọc trong hộp Thêm/Sửa thuật ngữ. Chưa có: chip Hán-Việt ở form sửa bản dịch trong màn đọc, nút "Áp cả truyện", văn phong dịch (admin).
- Màu theo bìa (Ambient.kt): nền trang Chi tiết ngả màu chủ đạo của bìa. Hero Khám phá cũng nhuộm nhẹ theo bìa từng trang.
- 25/10: form sửa bản dịch có chip Hán-Việt (chữ Hán gốc của từ đang bôi + các cách đọc, chạm để điền "Bản sửa"). Compile qua, chưa thử trên máy.
- 25/10: chip "Google Dịch" (mở trình duyệt, tra chữ Hán của từ bôi hoặc cả câu); màn VPS có Sửa nhãn + Xoá dòng host; sheet job ở tab Worker có "Xoá truyện này". Nhật ký lỗi cục bộ (ErrorLog.kt: bắt crash, 60 lỗi mới nhất, xem ở Quản trị → Nhật ký lỗi). Knob gemini_catalog/nvidia_catalog đã làm từ trước (AdminCrawl.kt). Đã chạy thử trên máy ảo: chip Hán-Việt + Google Dịch, nút xoá truyện ở sheet job, menu VPS.
- Bỏ: ink_transition (đã có chuyển cảnh lò xo), skeleton (đã có).
