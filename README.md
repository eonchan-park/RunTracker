# RunTracker (달리기 앱)

Kotlin + Jetpack Compose로 만든 안드로이드 달리기 앱입니다.

## 화면 흐름
1. **첫 화면**: 달리는 이모티콘 + 시작 버튼
2. **카운트다운(5초)** → **달리는 중**: 달린 시간 / 거리 / 현재 페이스 / 속도, 하단에 일시정지·멈춤
3. **일시정지**: 수치가 멈추고 하단에 계속·종료
4. **결과**: 화면 최상단에 **이동 경로 지도**, 아래에 총 시간 / 총 거리 / 평균 속도

멈춤과 종료는 모두 기록을 끝내고 결과 화면으로 이동합니다.

## 음성 안내
- **1km마다**: "1킬로미터 달렸어요. 달린 시간은 6분 12초, 평균 속도는 시속 9.7킬로미터예요." (현재 거리 / 시간 / 평균 속도)
- **종료 시**: "수고하셨어요. 총 달린 거리는 5.23킬로미터, 총 시간은 …, 평균 속도는 …"
- 한국어 TTS 음성을 사용하며, 여성 음성이 있으면 우선 선택합니다. 안드로이드는 음성 성별 정보를 공식 제공하지 않아서, 별도 지정이 없으면 기기의 기본 한국어 음성(구글 TTS는 여성)을 사용하고 음높이를 살짝 높였습니다. 다른 음성을 원하면 폰 설정 → 텍스트 음성 변환(TTS)에서 바꿀 수 있습니다.
- 음악을 듣는 중이면 안내하는 동안만 소리가 작아집니다. 화면이 꺼져 있어도 안내됩니다.
- 한국어 음성 데이터가 없으면 안내가 나오지 않습니다. 설정 → 텍스트 음성 변환에서 한국어를 설치하세요.

## 경로 지도
- OpenStreetMap 기반(osmdroid, API 키 불필요). 경로는 주황색 선, 출발은 초록 점, 도착은 빨간 점입니다.
- 지도 타일은 인터넷이 필요합니다. 연결이 없어도 경로선과 출발/도착 표시는 그려집니다.
- 일시정지 중 이동한 구간은 선으로 이어 그리지 않습니다.
- 앱을 많은 사용자에게 배포할 때는 OpenStreetMap 무료 타일 사용 정책을 확인하고, 필요하면 유료/자체 타일 서버나 Google Maps SDK로 바꾸세요.

## GPS 동작 방식
- `RunService`: 포그라운드 서비스 + Google Fused Location(1초 간격, 고정밀). 화면이 꺼져도 기록이 이어지고, 알림에 시간·거리가 표시됩니다.
- `RunTracker`: 상태·타이머·거리/속도 계산·경로 기록·1km 안내 판단.
- 시작 버튼을 누르면 카운트다운 5초 동안 GPS 신호와 음성 엔진을 미리 준비합니다.
- 보정 로직 (`RunTracker.kt` 상단 상수로 조정): 정확도 25m 초과 위치 버림 / 초속 12m 초과 이동 버림 / 3m 미만 이동은 제자리 떨림으로 처리 / 속도는 평활화 / 일시정지 중 이동 거리는 제외.

## APK 만들기 (설치 파일)
소스 zip 자체는 폰에서 풀어서 실행할 수 없고, 아래 방법 중 하나로 `.apk`를 만들어야 합니다.

### 방법 A. Android Studio (PC)
1. Android Studio에서 `RunTracker` 폴더를 Open → Gradle Sync 완료 대기
2. 메뉴 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
3. 완료 알림의 *locate*를 누르면 `app/build/outputs/apk/debug/app-debug.apk`가 있습니다.
4. 폰으로 옮겨 설치합니다. (폰에서 "출처를 알 수 없는 앱 설치" 허용 필요)

터미널을 쓴다면: `./gradlew assembleDebug` (Windows는 `gradlew.bat assembleDebug`)

### 방법 B. GitHub에서 자동 빌드 (PC에 Android Studio가 없을 때)
1. 이 폴더를 GitHub 저장소에 올립니다. (`.github/workflows/build-apk.yml` 포함)
2. 저장소의 **Actions → Build APK → Run workflow**
3. 끝나면 실행 결과 화면의 **Artifacts → RunTracker-debug-apk** 를 내려받습니다.

### 실제 배포용 서명 (선택)
디버그 APK는 지인에게 직접 설치해 주는 용도로는 충분합니다. 스토어 등록·정식 배포에는 본인 키로 서명하세요.
1. 키 만들기: `keytool -genkey -v -keystore release.jks -alias run -keyalg RSA -keysize 2048 -validity 10000`
2. 프로젝트 루트에 `keystore.properties` 작성
   ```
   storeFile=release.jks
   storePassword=비밀번호
   keyAlias=run
   keyPassword=비밀번호
   ```
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
   (Google Play는 APK 대신 `./gradlew bundleRelease`로 만드는 AAB를 요구합니다.)
4. `release.jks`와 `keystore.properties`는 잃어버리거나 공개하지 마세요. (`.gitignore`에 이미 제외됨)

`keystore.properties`가 없으면 release 빌드도 디버그 키로 서명되어 설치는 되지만 스토어용은 아닙니다.

## 파일 구성
```
app/src/main/java/com/example/runtracker/
  MainActivity.kt   권한 요청, 위치 설정 확인, 지도 설정
  RunTracker.kt     상태/타이머/거리·속도/경로 기록, 1km·종료 안내 시점
  RunService.kt     GPS 포그라운드 서비스 + 알림
  Speaker.kt        음성 안내(TTS)
  UI.kt             Compose 화면 4종
  RouteMap.kt       결과 화면 경로 지도
  Format.kt         시간/페이스/거리 표시 및 음성 문구
```

## 알려둘 점
- 코드를 작성한 환경에서 Android SDK 서버에 접근할 수 없어 **빌드·실기기 테스트는 하지 못했습니다.** Sync/빌드 중 오류가 나면 메시지를 알려주세요.
- 요구 사항: Android 8.0(API 26) 이상, Google Play 서비스가 있는 기기.
- 일부 폰은 백그라운드 위치를 강하게 제한합니다. 달리는 동안 끊기면 설정 → 앱 → 배터리에서 "제한 없음"으로 바꿔 보세요.
- 아직 없는 기능: 러닝 기록 저장(히스토리), 음성 안내 끄기 설정.
