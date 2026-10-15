# Indoorpositioning APP with Plain Fingerprinting

Wi-Fi RSSI 핑거프린팅(KNN) 기반 실내측위 안드로이드 앱.
고속 Wi-Fi·IMU 수집기로 만든 라디오맵을 서버에 올려 두고, 스마트폰의 Wi-Fi 스캔 결과로 현재 위치를 계산해 도면 위에 표시합니다.

## 전체 흐름

```
고속수집기(RSSI·IMU) → 후처리(PDR → 라디오맵) → Supabase(라디오맵·도면)
                                                    ↓
              Android 앱 ── RSSI 벡터 ──→ 측위 서버(KNN, Render)
                   ↑                              │
                   └────────── (x, y) 좌표 ───────┘
```

## 기능

- **맵 선택**: 서버의 `GET /maps`로 등록된 라디오맵 목록과 도면 정보를 받아 선택
- **Wi-Fi 스캔**: `WifiManager`로 주변 AP를 스캔해 `BSSID → RSSI` 벡터 생성 (스캔이 제한되면 캐시된 결과 사용)
- **위치 계산 요청**: `POST /locate`로 맵 ID와 RSSI 벡터를 보내고 KNN(k=3) 결과 좌표를 받음
- **도면 표시**: Glide로 도면 이미지를 불러오고, 미터 좌표를 도면 원점·실측 크기 기준으로 픽셀 좌표로 변환해 위치 마커와 좌표 라벨을 그림

## API

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/maps` | - | 맵 목록 (도면 URL, AP 목록, 이미지 크기, 실측 크기, 원점) |
| POST | `/locate` | `map_id`, `rssi_vector`, `k` | `x`, `y`, `map_id`, `matched_aps` |

서버 주소는 `ApiService.kt`의 `BASE_URL`에서 바꿀 수 있습니다.

## 기술 스택

- Kotlin, Android (minSdk 26, compileSdk 36)
- Retrofit 2 + Gson, Kotlin Coroutines
- Glide
- Supabase Kotlin SDK

## 구조

```
app/src/main/java/com/example/indoorpositioning/
├── MainActivity.kt    # 권한, 맵 로드, Wi-Fi 스캔, 위치 요청
├── ApiService.kt      # 데이터 모델, Retrofit API
└── FloorPlanView.kt   # 도면 위 위치 마커 표시 (m → px 변환)
```

## 실행

1. Android Studio로 프로젝트 열기
2. 위치 권한과 Wi-Fi 권한 허용 (Wi-Fi 스캔에 위치 권한이 필요)
3. 맵을 고른 뒤 "📍 현재 위치 찾기" 버튼을 눌러 현재 위치 확인

## 관련 저장소

- 고속 Wi-Fi·IMU 수집기: https://github.com/ysh8927/NNL_High-Speed-Collector_final

## 개발자

- 유승현 (ysh8927) · 한림대학교 AI융합연구원 NNL
