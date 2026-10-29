# 해둠 (com.nia.haedum)

집안일, 마지막으로 언제 했지? 탭 한 번으로 "해둠".

- 앱 ID(패키지): `com.nia.haedum`
- 현재 버전: **1.1.1 (versionCode 5)**
- Node 없는 순수 Android(Java) 앱. 화면은 `app/src/main/assets/index.html` 한 파일
- 기록: 폰 안에만 저장 (서버 없음)
- 주기 초과 알림: 정해 둔 주기를 넘긴 첫날 오전 9시에 폰이 직접 알림 (예: 30일 주기 → 31일째 9시)
- 하단 애드몹 배너 광고

## 폴더 안내

| 위치 | 내용 |
|---|---|
| `app/src/main/assets/index.html` | 앱 화면 전체 (디자인·기능) |
| `app/src/main/java/com/nia/haedum/MainActivity.java` | 화면 띄우기, 뒤로가기, 광고 배너 |
| `app/src/main/java/com/nia/haedum/Reminders.java` | 주기 초과 알림 예약 |
| `app/src/main/res/values/ads.xml` | 애드몹 앱 ID, 배너 광고 단위 ID |
| `app/build.gradle.kts` | 버전 번호 (`versionCode`, `versionName`) |
| `store/` | 스토어 아이콘, 그래픽 이미지, 스크린샷 |

## Android Studio에서
1. 이 저장소를 내려받아 폴더를 열기
2. Gradle sync가 끝나면 폰 연결 후 ▶ 실행 (이때는 구글 **테스트 광고**가 나옴)
3. 업데이트할 때는 `app/build.gradle.kts`의 `versionCode`를 1 올리기
4. Build › Clean Project → Build › Generate Signed App Bundle or APK › **Android App Bundle**
   - 키는 처음 만든 것 그대로 (키 파일은 이 저장소에 올리지 말 것)
5. Play Console에 `app-release.aab` 업로드

## 광고 주의
- ▶로 실행한 개발 버전은 테스트 광고, 서명된 출시 버전만 내 광고가 나와요
- 내 광고를 직접 누르면 애드몹 계정이 정지될 수 있어요
- 키보드가 올라와 있는 동안은 광고를 숨겨요

## 버전 기록
- **1.1.1 (5)** 광고 ID 권한 명시
- **1.1.0 (4)** 하단 애드몹 배너 추가
- **1.0.2 (3)** 버전 번호만 올림
- **1.0.1 (2)** 화면 확대 막기, 추가 버튼을 엄지 높이로
- **1.0.0 (1)** 첫 출시
