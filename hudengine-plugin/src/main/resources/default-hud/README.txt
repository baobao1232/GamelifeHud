# HUD mẫu của GamelifeHUD

Thư mục này được tạo trong lần khởi động đầu tiên để plugin có thể hoạt động ngay mà không cần cấu hình thêm.
Đây chỉ là một ví dụ, không phải cấu hình bắt buộc: hãy xóa những thứ bạn không cần.

status    khuôn mặt người chơi, thanh máu và thanh đói, ở góc trên bên trái
coords    tọa độ và hướng nhìn, ở giữa phía dưới
compass   thanh la bàn, ở giữa phía trên

Mọi thứ được hiển thị đều lấy từ các giá trị mà engine tự xử lý, vì vậy HUD có thể hoạt động mà không cần cài thêm plugin nào khác. Phần chữ sử dụng font mặc định của Minecraft được tích hợp sẵn trong plugin, nên cũng không cần thêm file font.

Chỉnh sửa cấu hình rồi chạy /hudengine reload. Vị trí và nội dung chữ sẽ được cập nhật ngay lập tức; các hình ảnh, font mới hoặc các thành phần được di chuyển sẽ yêu cầu người chơi vào lại server, vì đó là lúc client nhận resource pack mới.

Để bắt đầu lại từ đầu, hãy xóa thư mục này rồi khởi động lại server. Nếu không muốn thư mục này tự xuất hiện lại, đặt starter-hud: false trong config.yml.

# HUD mẫu của HUDEngine

Thư mục này được tạo trong lần khởi động đầu tiên để plugin có thể hoạt động ngay. Đây chỉ là một ví dụ, không phải cấu hình được khuyến nghị: hãy xóa mọi thứ bạn không cần.

status    khuôn mặt người chơi, thanh máu và thanh đói, ở góc trên bên trái
coords    tọa độ và hướng nhìn, ở giữa phía dưới
compass   thanh la bàn, ở giữa phía trên

Mọi thứ được hiển thị đều do engine tự lấy từ các giá trị của nó, vì vậy HUD hoạt động mà không cần bất kỳ plugin bên thứ ba nào. Phần chữ sử dụng font mặc định của Minecraft được tích hợp trong plugin, nên cũng không cần file font.

Chỉnh sửa rồi chạy /hudengine reload. Vị trí và nội dung chữ sẽ cập nhật ngay; hình ảnh, font mới và các thành phần được di chuyển sẽ yêu cầu người chơi vào lại server, vì khi đó client mới nhận resource pack mới.

Để bắt đầu lại, hãy xóa thư mục này và khởi động lại server. Nếu không muốn nó xuất hiện lại, đặt starter-hud: false trong config.yml.
