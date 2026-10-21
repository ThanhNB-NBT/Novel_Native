# Đối chiếu với app Flutter (../Novel_Persona/app/lib)

Mục tiêu: giống bản Flutter, TRỪ Tu Tiên (screens/cultivation/*, cultivation.dart, tab giữa).

## Đã có (0.2.0)
- Khám phá 4 mục + Xem tất cả (trộn nguồn, xoay theo ngày), Tìm kiếm không dấu + lọc
- Chi tiết truyện, mục lục, theo dõi, đọc tiếp, dấu trang
- Màn đọc: chờ dịch (poll), tự dịch trước 15 chương, lưu tiến độ + % cuộn, cỡ chữ/giãn dòng/lề/màu/căn đều, mục lục, báo lỗi chương
- Tủ truyện, Thông báo (pull 7 ngày, xoá), Tôi (giao diện, tự dịch trước), Đăng nhập email

## Còn thiếu — theo thứ tự nên làm
1. Màn đọc nâng cao — reader.dart, reader_settings.dart, reader_pager.dart, reader_panels.dart
   - chế độ lật trang, font chữ, phím âm lượng, tự cuộn, vuốt quá mép đổi chương, đánh dấu thuật ngữ
2. Sửa bản dịch — reader_edit_form.dart, reader_dialogs.dart (edit_chapter_vi/para, gợi ý AI goi-y-dich, dịch lại chương)
3. Thuật ngữ truyện — screens/novel/glossary.dart, data/glossary.dart
4. Nghe truyện (TTS) — tts.dart
5. Đọc offline — offline.dart, screens/library/offline_library.dart
6. Chi tiết: chọn nhiều chương để dịch lại, nút Dịch, mã truyện — novel_detail.dart
7. Hồ sơ: tên hiển thị, ảnh đại diện, chuỗi ngày đọc — data/profile.dart, account/edit_profile.dart, settings.dart
8. Hàng đợi dịch — screens/library/queue.dart, data/queue.dart
9. Hướng dẫn, Có gì mới, kiểm tra cập nhật — account/guide.dart, whats_new.dart, update.dart
10. Lịch âm — account/lunar_calendar.dart, lunar.dart
11. Quản trị (admin) — screens/admin/*, data/admin.dart (lớn nhất ngoài Tu Tiên)
12. Giao diện: theme.dart (màu thương hiệu), skeleton, ink_transition, ambient
