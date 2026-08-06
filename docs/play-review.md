# Play 콘솔 제출용 문구 모음

민감 권한 4종 + 데이터 보안 양식 답안. **콘솔에 그대로 복사해서 쓰는 용도**입니다.
콘솔 심사는 영문으로 진행되는 경우가 많아 항목마다 한국어와 영어를 같이 둡니다.

> 여기 적힌 내용은 모두 코드에서 확인된 사실입니다. 심사 답변은 앱의 실제
> 동작과 어긋나면 그 자체가 반려·정지 사유가 되므로, 기능을 바꾸면 이 문서도
> 같이 고쳐야 합니다.

---

## 1. 포그라운드 서비스 — `specialUse` (가장 큰 반려 리스크)

**어디에** — 콘솔 → 앱 콘텐츠 → 포그라운드 서비스 권한 (Foreground service permissions)

Android 14 부터 요구되는 매니페스트 선언은 이미 되어 있습니다
(`AndroidManifest.xml` 의 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`). 콘솔에서는
**왜 정의된 다른 유형으로는 안 되는지**를 글로 설명해야 합니다. 이 질문에
답하지 못하는 것이 `specialUse` 반려의 대부분입니다.

### 한국어

> TodoLock 의 핵심 기능은 사용자가 기기 잠금을 해제하는 순간 그날 남은 할 일
> 목록을 화면에 띄우는 것입니다. 이 앱의 존재 이유가 이 한 가지 동작입니다.
>
> 이를 위해 앱은 `ACTION_USER_PRESENT` 브로드캐스트를 받아야 합니다. Android 8
> 부터 이 브로드캐스트는 암시적 브로드캐스트 제한 대상이어서 매니페스트에
> 선언한 리시버로는 받을 수 없고, 실행 중인 프로세스에서 런타임에 등록해야만
> 수신됩니다. 따라서 등록을 유지할 프로세스가 살아 있어야 하며, 포그라운드
> 서비스가 이를 위한 유일한 지원 수단입니다.
>
> `dataSync`, `mediaPlayback`, `location`, `phoneCall`, `connectedDevice`,
> `mediaProjection`, `camera`, `microphone`, `health`, `remoteMessaging` 은
> 모두 이 작업의 성질과 무관합니다. 데이터를 동기화하거나 센서·미디어를
> 사용하지 않습니다. `shortService` 는 수 분 내에 끝나는 작업을 위한 유형이라,
> 사용자가 언제 잠금을 해제할지 알 수 없는 이 기능에는 쓸 수 없습니다.
> `systemExempted` 에 해당하는 시스템 앱도 아닙니다. WorkManager·JobScheduler
> 는 잠금해제 시점을 관측할 수 없어 대안이 되지 못합니다.
>
> 서비스는 네트워크를 사용하지 않고(앱에 `INTERNET` 권한이 없습니다) 어떤
> 데이터도 수집하거나 전송하지 않습니다. 감지하는 것은 '잠금이 해제되었다'는
> 사실 하나이며 기록하지 않습니다. 서비스 알림은 중요도 MIN 으로 표시되고,
> 사용자는 앱 설정의 스위치 하나로 이 기능과 서비스를 완전히 끌 수 있습니다.

### English (콘솔 입력용)

> TodoLock's core and defining feature is to display the user's remaining tasks
> for today at the moment they unlock their device. This single behaviour is the
> entire purpose of the app.
>
> To do this the app must receive the `ACTION_USER_PRESENT` broadcast. Since
> Android 8 this broadcast is subject to implicit-broadcast restrictions and
> cannot be delivered to a manifest-declared receiver; it is only delivered to a
> receiver registered at runtime by a live process. A foreground service is
> therefore the only supported way to keep that registration alive.
>
> No defined foreground service type applies. The work involves no data
> synchronisation, media, location, camera, microphone, phone call, connected
> device, screen capture, health data, or messaging. `shortService` is intended
> for work that completes within a few minutes and cannot be used, because the
> app cannot know when the user will next unlock the device. The app is not a
> system app, so `systemExempted` does not apply. WorkManager and JobScheduler
> cannot observe the unlock event and are not viable alternatives.
>
> The service performs no networking — the app does not declare the `INTERNET`
> permission — and collects or transmits no data whatsoever. The only thing
> observed is the fact that the device was unlocked, and it is not recorded. The
> service notification is posted at MIN importance, and a single switch in the
> app's settings disables the feature and stops the service entirely.

---

## 2. 다른 앱 위에 표시 — `SYSTEM_ALERT_WINDOW`

오해를 부르기 쉬운 권한이라 **"화면을 덮는 오버레이를 그리지 않는다"** 를 먼저
밝히는 편이 안전합니다. 실제 용도는 백그라운드 액티비티 실행 예외입니다.

> 이 권한은 화면 위에 오버레이를 그리기 위한 것이 아닙니다. Android 10 부터
> 백그라운드에서의 액티비티 실행이 차단되며, `SYSTEM_ALERT_WINDOW` 가 허용된
> 앱은 이 제한에서 공식적으로 면제됩니다. 잠금해제 직후 할 일 창을 띄우는 것이
> 이 앱의 핵심 기능이므로 이 면제가 필요합니다. 창은 사용자가 닫을 수 있는
> 일반 액티비티이며, 다른 앱 위에 상주하거나 사용자 조작을 가리지 않습니다.
> 권한이 없으면 앱은 팝업 대신 알림으로 대체 동작하고, 기능이 제한된다는 사실을
> 앱 안에서 안내합니다.

> This permission is not used to draw an overlay on the screen. Since Android 10,
> starting an activity from the background is blocked, and apps holding
> `SYSTEM_ALERT_WINDOW` are explicitly exempted from that restriction. The app
> needs that exemption to show the task window immediately after unlock, which is
> its core feature. The window is an ordinary, user-dismissible activity; it does
> not persist over other apps or obstruct user interaction. Without the
> permission the app falls back to a notification and tells the user in-app that
> the feature is limited.

---

## 3. 전체 화면 알림 — `USE_FULL_SCREEN_INTENT`

**어디에** — 콘솔 → 앱 콘텐츠 → 전체 화면 인텐트 권한

Play 는 이 권한을 **알람 · 통화가 핵심 기능인 앱**으로 제한합니다. 우리 앱에서
정당화가 성립하는 쪽은 **미리 알림(사용자가 시각을 직접 지정하는 알람)** 입니다.

> 사용자는 할 일마다 알림 시각을 직접 지정할 수 있고, 이 앱의 미리 알림은 그
> 시각에 울리는 사용자 설정 알람입니다. 사용자가 알림 방식으로 '전체 팝업' 을
> 선택한 경우에만 전체 화면 인텐트를 사용하며, 기본값이 아닙니다. 앱 설정에서
> '알림창' 또는 '헤드업' 을 고르면 전체 화면 표시는 사용되지 않습니다. 사용자가
> 명시적으로 요청하지 않은 상황에서 화면을 점유하지 않습니다.

> Users set an explicit time for each task's reminder, and the app's reminders
> are user-scheduled alarms that fire at that time. A full-screen intent is used
> only when the user has chosen the "full popup" reminder style; it is not the
> default. Choosing "notification shade" or "heads-up" in settings means
> full-screen presentation is never used. The app never takes over the screen in
> a situation the user did not explicitly request.

⚠️ **주의** — 잠금해제 팝업의 대체 경로(오버레이 권한이 없을 때)도 전체 화면
인텐트를 쓰고 있습니다. 이쪽은 "알람" 이라는 설명이 성립하지 않으므로, 심사에서
문제가 되면 **그 경로만 일반 알림으로 내리는 것**이 가장 깔끔한 후퇴입니다.

---

## 4. 정확한 알람 — `SCHEDULE_EXACT_ALARM`

사용자가 허용/거부하는 권한이라 별도 콘솔 양식은 없지만, 정책 문의가 오면:

> 사용자가 할 일마다 분 단위로 지정한 시각에 알림을 울리기 위해 사용합니다.
> 이는 사용자가 직접 설정한 리마인더이므로 정확한 시각 전달이 기능의 본질입니다.
> 권한이 없으면 앱은 `setAndAllowWhileIdle` 로 자동 대체하여 동작을 계속하며,
> 몇 분 늦을 수 있다는 사실을 사용자에게 알립니다. 권한 없이도 앱은 정상
> 동작합니다.

---

## 5. 배터리 최적화 예외 — `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ⚠️ 결정 필요

**이 권한이 4개 중 정책상 가장 취약합니다.** Play 는 이 권한 요청을 좁은
사용 사례로만 허용하며, 반려 사례가 흔합니다. 매니페스트에 남긴 기존 주석도
"사이드로드 전용" 이라고 적혀 있었습니다 — 스토어 출시로 방향이 바뀐 만큼
여기서 판단이 필요합니다.

선택지:

| 안 | 내용 | 결과 |
|---|---|---|
| **A. 유지하고 정당화** | 아래 문구로 제출 | 통과하면 지금 UX 그대로. 반려 시 재제출 지연 |
| **B. 권한을 빼고 안내로 대체** | 매니페스트에서 삭제하고, 설정 화면에서 시스템 배터리 설정으로 **보내기만** 함 | 정책 리스크 소멸. 사용자가 목록에서 앱을 직접 찾아야 해 이탈 증가 |

**저는 B 를 권합니다.** 이미 삼성 절전 대응 때문에 `앱 정보 열기` 로 보내는
경로를 만들어 두었고, 그 UX 를 배터리 예외에도 적용하면 됩니다. 핵심 기능
4개를 한 번에 심사받는 상황에서 가장 약한 카드를 스스로 버리는 편이
전체 통과 확률을 올립니다.

A 로 갈 경우 제출 문구:

> 이 앱의 핵심 기능은 사용자가 잠금을 해제하는 순간 할 일을 띄우는 것이며,
> 이를 위해 잠금해제 브로드캐스트를 수신할 프로세스가 유지되어야 합니다.
> 배터리 최적화가 적용되면 절전 중 프로세스가 종료되어 잠금해제를 감지하지
> 못하고 핵심 기능이 조용히 실패합니다. 권한은 사용자가 설정 화면에서 직접
> 누를 때만 요청하며, 앱 실행 시 자동으로 요청하거나 반복해서 요구하지
> 않습니다. 거부해도 앱은 계속 동작하고, 팝업이 뜨지 않을 수 있다는 사실을
> 설정 화면에 표시합니다.

---

## 6. 데이터 보안 양식 (Data safety)

| 질문 | 답 |
|---|---|
| 앱이 사용자 데이터를 수집 또는 공유합니까? | **아니요** |
| 전송 중 암호화 | 해당 없음 (네트워크 통신 없음) |
| 사용자가 데이터 삭제를 요청할 수 있습니까? | 해당 없음 (개발자가 보관하는 데이터 없음) |
| 데이터 수집이 앱 기능에 필수입니까? | 해당 없음 |
| 독립 보안 검토를 받았습니까? | 아니요 |

근거: 앱에 `INTERNET` 권한이 없어 전송이 기술적으로 불가능하고, 광고 · 분석 ·
크래시 리포트 SDK 가 포함되어 있지 않습니다.

**주의** — Android 자동 백업(`allowBackup="true"`) 으로 앱 데이터가 사용자
Google 계정에 백업될 수 있습니다. 개발자가 접근할 수 없으므로 Play 기준의
'수집' 에 해당하지 않지만, 개인정보처리방침에는 명시해 두었습니다
(`docs/privacy.html` 3항). 백업 자체가 불필요하다고 보시면
`allowBackup="false"` 로 바꾸면 이 논점이 사라집니다.

---

## 7. 심사자용 테스트 안내 (반려 예방에 가장 효과적)

**어디에** — 콘솔 → 앱 콘텐츠 → 앱 액세스 권한 / 심사 관련 안내

핵심 기능이 "잠금해제 시 팝업" 인데 심사자는 에뮬레이터에서 이걸 재현하지
못할 가능성이 높습니다. **기능이 확인되지 않으면 권한 정당화도 받아들여지지
않습니다.** 그래서 재현 절차를 명시적으로 적어줍니다.

> 로그인이나 계정이 필요하지 않습니다. 모든 기능을 즉시 사용할 수 있습니다.
>
> 핵심 기능(잠금해제 시 할 일 표시)을 확인하는 방법:
> 1. 앱을 열고 `＋ 할 일 추가` 로 할 일을 하나 등록합니다 (기한 기본값이 오늘입니다).
> 2. 우측 상단 ⚙ → `권한 · 도구` → `권한 주기` 로 '다른 앱 위에 표시' 를 허용합니다.
> 3. 화면을 끄고 다시 잠금을 해제하면 할 일 창이 뜹니다.
>
> 잠금해제 없이 결과 화면만 확인하려면 ⚙ → `팝업 미리보기` 를 누르면 됩니다.
>
> 포그라운드 서비스는 ⚙ 첫 번째 카드의 스위치로 끌 수 있습니다.

> No login or account is required; all functionality is available immediately.
>
> To verify the core feature (showing tasks on unlock):
> 1. Open the app and add a task with `＋ 할 일 추가` (the due date defaults to today).
> 2. Tap ⚙ (top right) → `권한 · 도구` → `권한 주기` and grant "display over other apps".
> 3. Turn the screen off, then unlock the device — the task window appears.
>
> To see the same window without unlocking, tap ⚙ → `팝업 미리보기`.
>
> The foreground service can be turned off with the switch in the first card of ⚙.

---

## 8. 남은 준비물 (이 문서 범위 밖)

- **업로드 키스토어** — 현재 release 가 debug 키로 서명되어 있어 업로드 자체가
  거부됩니다 (`app/build.gradle.kts`). Play App Signing 등록 필요.
- **AAB 빌드** — 신규 앱은 APK 가 아닌 `bundleRelease` 결과물을 올립니다.
- **`targetSdk` 상향** — 34 는 신규 앱 요구 수준보다 낮습니다. 올린 뒤 동작 재검증 필요.
- **방침 URL** — `docs/privacy.html` 을 `hj0128.com` 에 올리고 그 주소를 콘솔에 입력.
  GitHub Pages 를 `/docs` 로 켜면 `https://hj0128.github.io/TodoLock/privacy.html` 로도 즉시 사용 가능합니다.
- **연락 메일** — 방침 문서에 `contact@hj0128.com` 을 적어 두었습니다. 수신되는
  주소로 만들거나 다른 주소로 교체해야 합니다.
