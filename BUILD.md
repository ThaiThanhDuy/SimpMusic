# Build APK SimpMusic

Hướng dẫn build APK Android cho SimpMusic từ mã nguồn và cài lên điện thoại.

## 1. Chuẩn bị

| Cần có | Ghi chú |
|---|---|
| JDK 21 | Kiểm tra bằng `java -version` |
| Android SDK | Có **platform 37** (project compile với 37) và build-tools (vd 36.0.0) |
| `ANDROID_HOME` | Trỏ tới thư mục SDK, vd `~/Android/Sdk` |
| RAM | Gradle dùng tới 5 GB (`-Xmx5120m` trong `gradle.properties`) |

### Tải mã nguồn kèm submodule

Thư mục `core/` là một git submodule. Trên nhánh `hnp`, submodule này trỏ tới fork `ThaiThanhDuy/core` (nhánh `hnp`), vì code nghe nhạc local có sửa trong `core`. Nếu thư mục này trống, build sẽ báo lỗi `Configuring project ':loginSync' without an existing directory`.

```bash
git clone -b hnp --recursive https://github.com/ThaiThanhDuy/SimpMusic.git
# hoặc, nếu đã clone rồi:
git submodule update --init --recursive
```

Xem thêm mục 7 về cách commit và push khi có sửa trong `core`.

## 2. Chọn bản Full hay FOSS

Biến thể được chọn bằng cờ `isFullBuild` trong `gradle.properties`:

| | `isFullBuild=true` (Full) | `isFullBuild=false` (FOSS) |
|---|---|---|
| Sentry (báo crash) | Có | Không |
| Google Cast | Có | Không |
| Last.fm | Có | Không |
| Google Play Services | Có | Không |

Không muốn sửa file thì thêm `-PisFullBuild=false` vào lệnh Gradle.

Bản Full đọc key từ `local.properties` (`SENTRY_DSN`, `SENTRY_AUTH_TOKEN`, `LASTFM_API_KEY`, `LASTFM_SECRET`). Thiếu các key này vẫn build được, chỉ là các tính năng đó không hoạt động.

## Lệnh nhanh (sau khi sửa code)

Chạy ở thư mục gốc của project:

```bash
# Chỉ kiểm tra code có biên dịch được không (nhanh nhất, không ra APK)
./gradlew :androidApp:compileReleaseKotlin

# Build APK release đã ký (cần keystore.properties, xem mục 4)
./gradlew :androidApp:assembleRelease

# Cài đè lên điện thoại qua cáp USB (-r: giữ nguyên dữ liệu app)
adb install -r androidApp/build/outputs/apk/release/androidApp-arm64-v8a-release.apk
```

Build release xong thì APK nằm trong `androidApp/build/outputs/apk/release/`. Lấy file `androidApp-arm64-v8a-release.apk` gửi qua điện thoại là được.

Nếu sửa code trong thư mục `core/` (submodule) mà build không ăn thay đổi, chạy `./gradlew clean` rồi build lại.

## 3. Build bản debug (nhanh, không cần keystore)

```bash
./gradlew :androidApp:assembleDebug
```

- File ra: `androidApp/build/outputs/apk/debug/`
- Tự ký bằng debug key của máy
- Package `com.maxrave.simpmusic.dev`, cài song song được với bản chính thức
- Không thu gọn code, nên file to hơn và chạy chậm hơn bản release

## 4. Build bản release đã ký

### 4.1. Tạo keystore (làm một lần)

```bash
keytool -genkeypair -v -keystore simpmusic.jks -alias mykey \
  -keyalg RSA -keysize 2048 -validity 10000
```

Giữ kỹ `simpmusic.jks` và mật khẩu. Muốn cài đè bản cập nhật lên app đã cài thì phải ký bằng đúng key này. File đã nằm trong `.gitignore`.

### 4.2. Tạo `keystore.properties` ở thư mục gốc

```properties
storeFile=simpmusic.jks
storePassword=MẬT_KHẨU_KEYSTORE
keyAlias=mykey
keyPassword=MẬT_KHẨU_KEY
```

- Xem alias bằng `keytool -list -keystore simpmusic.jks`.
- File này cũng nằm trong `.gitignore`, **không commit**.

### 4.3. Build

```bash
./gradlew :androidApp:assembleRelease
```

File ra nằm trong `androidApp/build/outputs/apk/release/`, đã ký sẵn:

| File | Dùng cho |
|---|---|
| `androidApp-arm64-v8a-release.apk` | Hầu hết điện thoại hiện nay (~28 MB) |
| `androidApp-armeabi-v7a-release.apk` | Máy 32-bit đời cũ |
| `androidApp-x86_64-release.apk` | Máy ảo / Chromebook |
| `androidApp-universal-release.apk` | Chạy mọi máy (~55 MB) |

Lần build đầu mất khoảng 15 phút vì phải tải dependency. Các lần sau nhanh hơn.

Kiểm tra chữ ký:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose \
  androidApp/build/outputs/apk/release/androidApp-arm64-v8a-release.apk
```

Dòng đầu in ra `Verifies` là đã ký đúng.

### Cơ chế ký và kiểm tra "bản không chính thức"

Bản release của SimpMusic so chữ ký với key chính thức. Ký bằng key khác thì app hiện màn hình chặn `UnofficialBuildScreen`.

Khi có file `keystore.properties`:
- `androidApp/build.gradle.kts` (và `vivoAndroidApp/build.gradle.kts`) ký bằng keystore của bạn.
- Hai file đó đặt `BuildConfig.CHECK_OFFICIAL_BUILD = false`, nên `MainActivity` bỏ qua bước kiểm tra.

Không có file này (vd trên CI chính thức) thì mọi thứ chạy như bản gốc: APK không ký và vẫn kiểm tra chữ ký.

### Cách khác: script có sẵn

```bash
export KEYSTORE_PASSWORD='...' KEY_ALIAS='mykey' KEY_PASSWORD='...'
./build_and_sign_apk.sh --release --full   # hoặc --foss, --debug, --vivo
```

Script chạy `clean`, build, zipalign rồi ký bằng `apksigner` với `./simpmusic.jks`. Vì luôn clean trước nên mỗi lần chạy là build lại từ đầu.

## 5. Cài lên điện thoại

**Không cần cáp:** gửi file `.apk` qua Zalo, Telegram, Google Drive hoặc email, rồi mở trên điện thoại. Lần đầu máy sẽ hỏi cho phép "Cài ứng dụng không rõ nguồn gốc".

**Có cáp USB** (bật USB debugging trong tùy chọn nhà phát triển):

```bash
adb install androidApp/build/outputs/apk/release/androidApp-arm64-v8a-release.apk
```

## 6. Lỗi thường gặp

| Lỗi | Cách xử lý |
|---|---|
| `Configuring project ':loginSync' without an existing directory` | Chưa tải submodule: `git submodule update --init --recursive` |
| App mở ra màn hình "bản không chính thức" | Build release mà chưa có `keystore.properties`, hoặc APK được ký lại bằng key khác. Tạo file rồi build lại |
| Cài báo "Ứng dụng chưa được cài đặt" / xung đột gói | Máy đang có bản SimpMusic ký bằng key khác (vd bản chính thức). Gỡ bản đó trước |
| APK vẫn có đuôi `-unsigned` | Gradle không thấy `keystore.properties`. File phải nằm ở thư mục gốc project, đúng tên |
| Hết bộ nhớ khi build | Đóng bớt ứng dụng, hoặc giảm `-Xmx` trong `gradle.properties` |

## 7. Git: nhánh `hnp` và submodule `core`

Code riêng của bạn nằm ở nhánh `hnp` trên hai repo:

| Repo | Nhánh | Chứa gì |
|---|---|---|
| https://github.com/ThaiThanhDuy/SimpMusic | `hnp` | App: nhạc local, chia playlist theo thư mục/album/nghệ sĩ, ký bằng keystore riêng, file `BUILD.md` này |
| https://github.com/ThaiThanhDuy/core | `hnp` | Fork của `maxrave-dev/core`: phần sửa để player phát được file trong máy |

`.gitmodules` trỏ `core` sang fork của bạn, nên ai clone repo này cũng lấy được bản `core` có code nhạc local.

### Remote trên máy

- `origin` là repo gốc của maxrave, giữ nguyên để lấy code mới về.
- `fork` là repo của bạn. Nhánh `hnp` đã theo dõi `fork/hnp`, nên chỉ cần `git push`.
- Trong thư mục `core/` cũng vậy: `origin` là `maxrave-dev/core`, `fork` là `ThaiThanhDuy/core`, và đang ở nhánh `hnp`.

### Khi sửa code trong `core/`

Repo chính chỉ lưu con trỏ tới một commit của `core`, không lưu code của nó. Vì vậy phải push `core` **trước**, rồi mới commit con trỏ ở repo chính:

```bash
# 1. Commit và push trong core
cd core
git add -A
git commit -m "mô tả thay đổi"
git push
cd ..

# 2. Commit con trỏ core (cùng các sửa khác) ở repo chính
git add core
git commit -m "mô tả thay đổi"
git push
```

Làm ngược lại (push repo chính trước) thì repo chính trỏ tới một commit `core` chưa có trên GitHub, và người clone về sẽ không lấy được.

### Những file không đưa lên git

- `simpmusic.jks` và `keystore.properties` (keystore và mật khẩu) nằm trong `.gitignore`. Không bao giờ commit hai file này.
- File `.apk` ở thư mục gốc cũng bị bỏ qua (`/*.apk`).
