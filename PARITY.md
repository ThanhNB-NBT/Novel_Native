# Đối chiếu với app Flutter (../Novel_Persona/app/lib)

Mục tiêu: giống bản Flutter, TRỪ Tu Tiên (screens/cultivation/*, cultivation.dart, tab giữa).

## Đã có (0.2.0)
- Khám phá 4 mục + Xem tất cả (trộn nguồn, xoay theo ngày), Tìm kiếm không dấu + lọc
- Chi tiết truyện, mục lục, theo dõi, đọc tiếp, dấu trang
- Màn đọc: chờ dịch (poll), tự dịch trước 15 chương, lưu tiến độ + % cuộn, cỡ chữ/giãn dòng/lề/màu/căn đều, mục lục, báo lỗi chương
- Tủ truyện, Thông báo (pull 7 ngày, xoá), Tôi (giao diện, tự dịch trước), Đăng nhập email

## Còn thiếu — theo thứ tự nên làm
1. ~~Màn đọc nâng cao — lật trang, font chữ, phím âm lượng, tự cuộn, vuốt quá mép đổi chương, gạch chân thuật ngữ, 14 font Sans + 4 Serif, 15 màu nền sáng/tối riêng, sheet có khung xem trước~~ (xong)
   - còn: ~12 font Sans tải từ Google Fonts (native chỉ có 6 font đóng gói + Roboto hệ thống), quà tu tiên cuối chương (bỏ)
2. ~~Sửa bản dịch~~ xong (Edit.kt): nhấn giữ đoạn → bôi từ + Thay từ (edit_chapter_vi, tên riêng học vào thuật ngữ), Lưu cả đoạn (edit_chapter_para), Gợi ý AI (goi-y-dich), báo lỗi. Chưa có: gợi ý chip thuật ngữ/Hán-Việt, nút "Áp cả truyện", tra Google Dịch
3. ~~Thuật ngữ truyện~~ xong (Glossary.kt, vào từ trang chi tiết): duyệt/bỏ gợi ý LLM, thêm/sửa/xoá, chọn nhiều để xoá, vá chương cũ + thanh tiến trình, báo cáo dịch sai. Chưa có: tra Hán-Việt, văn phong dịch + quét lỗi (admin), cập nhật bản offline sau vá
4. ~~Nghe truyện (TTS)~~ xong (Tts.kt): menu ⋮ → Nghe truyện, thanh điều khiển đáy, chọn giọng + tốc độ, đọc nền (foreground service), highlight + cuộn theo đoạn, tự sang chương sau. Có cả highlight ở chế độ lật trang + hẹn giờ tắt (15–60 phút / hết chương)
5. ~~Đọc offline~~ xong (Offline.kt): thẻ Đọc offline ở trang chi tiết (tải nền theo lô, chỉ kéo chương thiếu/đã đổi theo updated_at), màn Bản offline (Tủ truyện → icon), mất mạng thì truyện/mục lục/chương/tiến độ tự rơi về bản trên máy; chương đọc online và chương vừa vá thuật ngữ tự cập nhật xuống bản offline
6. Chi tiết: chọn nhiều chương để dịch lại, nút Dịch, mã truyện — novel_detail.dart
7. Hồ sơ: tên hiển thị, ảnh đại diện, chuỗi ngày đọc — data/profile.dart, account/edit_profile.dart, settings.dart
8. Hàng đợi dịch — screens/library/queue.dart, data/queue.dart
9. Hướng dẫn, Có gì mới — account/guide.dart, whats_new.dart (~~kiểm tra cập nhật~~ xong: Update.kt + release.yml, cần đặt secrets cho repo)
10. Lịch âm — account/lunar_calendar.dart, lunar.dart
11. Quản trị (admin) — screens/admin/*, data/admin.dart (lớn nhất ngoài Tu Tiên)
12. Giao diện: ~~theme.dart, header + triện, dock nổi, trang chủ 5 kiểu mục, chi tiết bìa mờ, màu nhấn/nền~~ (xong 0.2.0); còn skeleton, ink_transition, ambient (màu theo bìa)
