# TASK-03. 아이 프로필

`CLAUDE.md`를 먼저 읽고 시작한다. 이 작업이 끝나면 저장된 아이 프로필을 조회·추가·수정·호칭 변경·삭제할 수 있고, TASK-04(동화 만들기)가 그대로 가져다 쓸 **아이 정보 처리 서비스**가 준비되어 있어야 한다.

## 범위

**포함**: 아이 프로필 API 6개, 동화 만들기용 `ChildProfileResolver`, 테스트

**제외**: 동화 만들기 API 자체(TASK-04). DB 변경 없음 (V1의 `children`, `child_traits` 그대로 사용)

## 결정 사항

| 항목 | 결정 |
|---|---|
| 프로필 저장 | CRT-03 "다음에도 이 아이 정보로 만들기"를 체크했을 때만. 별도 체크박스 없음 |
| 기본 호칭 | 저장 시 `ChildLabelPolicy.nextLabel`로 "아이 A", "아이 B"… 비어 있는 가장 앞 글자 |
| 호칭 변경 | MY-01에서 부모가 변경. 1~30자. **AI로 보내지 않음** |
| 성향 | `Trait` 6개 중 0개 이상, 기타 입력 없음 |
| 좋아하는 것 | 최대 5개, 한 개당 20자, 공백 제거, 중복 합침 (`Interests.normalize`) |
| 프로필 삭제 | 소프트 삭제. 먼저 `StoryRepository.detachChild`로 동화와의 연결을 끊는다. 동화 내용은 `child_snapshot`이 있어서 영향 없음 |
| 다른 사람의 아이 | 403이 아니라 **404 `CHILD_NOT_FOUND`**. 존재 여부를 드러내지 않는다 |

---

## 작업 순서

각 단계 끝에 `./gradlew build` 통과 후 커밋. 명령은 `&&`로 이어서, 빌드가 실패하면 커밋하지 않는다. 마지막에 push.

1. 생년월 검증 규칙
2. 아이 프로필 서비스와 API
3. 동화 만들기용 `ChildProfileResolver`
4. 테스트, 문서 갱신

---

## 1. 생년월 검증 규칙

`domain.child.entity.BirthYmPolicy` (정적 유틸):

- 형식 `yyyy-MM` (예: `2021-03`). 형식 오류 → `INVALID_BIRTH_YM`
- 이번 달보다 미래면 `INVALID_BIRTH_YM`
- 만 12세(144개월)보다 많으면 `INVALID_BIRTH_YM`  // TODO(PM): 대상 연령 범위 확정
- 기준 시각은 `Clock`으로 받는다

ErrorCode 추가:

| 코드 | HTTP | 메시지 |
|---|---|---|
| INVALID_BIRTH_YM | 400 | 생년월을 다시 확인해 주세요. |

---

## 2. 아이 프로필 API

모두 로그인 필요. 경로의 `{id}`는 항상 `findByIdAndUserId`로 조회한다.

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/children` | - | `200 [ChildResponse]` (id 오름차순) |
| GET | `/api/children/{id}` | - | `200 ChildResponse` |
| POST | `/api/children` | `ChildProfileRequest` | `201 ChildResponse` |
| PUT | `/api/children/{id}` | `ChildProfileRequest` | `200 ChildResponse` |
| PATCH | `/api/children/{id}/display-name` | `{displayName}` | `200 ChildResponse` |
| DELETE | `/api/children/{id}` | - | `204` |

`ChildProfileRequest`:

```json
{
  "birthYm": "2021-03",
  "gender": "FEMALE",
  "traits": ["SHY", "SLOW_TO_WARM"],
  "interests": ["공룡", "블록 놀이"]
}
```

- `traits`, `interests`는 생략하면 빈 목록
- 없는 enum 값은 `VALIDATION_FAILED` (TASK-02의 처리 그대로)

`ChildResponse`:

```json
{
  "id": 3,
  "displayName": "아이 A",
  "birthYm": "2021-03",
  "ageMonths": 66,
  "gender": "FEMALE",
  "traits": ["SHY", "SLOW_TO_WARM"],
  "interests": ["공룡", "블록 놀이"]
}
```

- `ageMonths`는 `Clock` 기준으로 계산
- `traits`는 `Trait` 선언 순서로 정렬해서 내려준다 (매번 순서가 같도록)

### ChildService

`domain.child.service.ChildService`:

- `list(userId)`, `get(userId, childId)`
- `create(userId, profile)`: 호칭은 `ChildLabelPolicy.nextLabel(findDisplayNamesByUserId(userId))`
- `update(userId, childId, profile)`
- `rename(userId, childId, displayName)`
- `delete(userId, childId)`: `detachChild(childId)` → `child.delete(now)`. 같은 트랜잭션

### 화면 문구용 선택지 API (선택)

프론트가 성향 버튼 문구를 하드코딩하지 않도록:

| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/api/children/options` | `{traits: [{code, label}], genders: [{code, label}], interestsMax: 5, interestMaxLength: 20}` |

- 로그인 필요. `/api/children/{id}` 경로와 충돌하지 않게 주의 (`options`가 id로 해석되지 않도록)

---

## 3. 동화 만들기용 ChildProfileResolver

TASK-04에서 CRT-03 화면을 처리할 때 이 컴포넌트 하나만 부르면 되게 만든다. 이번 작업에서는 **API를 만들지 않고 서비스와 테스트만** 만든다.

`domain.child.service.ChildProfileResolver`:

```java
public record ChildSelection(
        Long childId,             // 저장된 프로필을 불러왔으면 id, 새로 입력했으면 null
        ChildProfile profile,     // 화면에 최종으로 입력된 값 (생년월, 성별, 성향, 좋아하는 것)
        boolean saveForNextTime   // "다음에도 이 아이 정보로 만들기" 체크 여부
) {}

public record ResolvedChild(
        Child child,              // 동화에 연결할 프로필. 없으면 null
        ChildSnapshot snapshot    // 동화에 저장하고 AI로 보낼 정보
) {}

ResolvedChild resolve(Long userId, ChildSelection selection);
```

처리 규칙 (다섯 경우 모두 테스트한다):

| # | childId | 체크 | 처리 | 동화의 child | snapshot 기준 |
|---|---|---|---|---|---|
| 1 | 없음 | O | 새 프로필 저장 (호칭 자동 부여) | 새 프로필 | 입력값 |
| 2 | 없음 | X | 저장하지 않음 | null | 입력값 |
| 3 | 있음 | O | 입력값으로 프로필 갱신 (값이 같으면 그대로) | 그 프로필 | 입력값 |
| 4 | 있음 | X | **프로필은 그대로**, 이번 동화에만 입력값 사용 | 그 프로필 | 입력값 |
| 5 | 다른 사람 것 | - | `CHILD_NOT_FOUND` | - | - |

- 4번: 저장된 프로필을 불러와 고쳤지만 체크를 풀었다면 프로필은 바꾸지 않는다. 동화는 같은 아이이므로 프로필과 연결한다.
- snapshot은 항상 **화면의 최종 입력값**으로 만든다 (`ChildSnapshot.of(birthYm, gender, traits, interests, today)`). 프로필에서 읽지 않는다.
- 입력값 검증(`BirthYmPolicy`, `Interests.normalize`)은 저장 여부와 상관없이 항상 한다.
- snapshot에는 호칭이 없다 (TASK-01에서 확인한 규칙 그대로).

---

## 4. 테스트

### 단위 테스트

| 대상 | 확인할 것 |
|---|---|
| `BirthYmPolicy` | `2021-03` 통과 / `2021-3`, `21-03`, `2021-13` 형식 오류 / 다음 달 오류 / 이번 달 통과 / 145개월 오류, 144개월 통과 |
| `ChildLabelPolicy` | (TASK-01 테스트 유지) |

### 통합 테스트 (MockMvc + Testcontainers, TASK-02의 로그인 헬퍼 사용)

| 시나리오 | 확인할 것 |
|---|---|
| 로그인 없이 `GET /api/children` | 401 |
| 추가 두 번 | "아이 A", "아이 B" / 목록 순서 |
| A 삭제 후 추가 | 새 프로필이 다시 "아이 A" |
| 호칭 변경 | 0자, 31자 → `INVALID_CHILD_NAME` / "첫째" 통과 |
| 좋아하는 것 6개 | `INTERESTS_LIMIT_EXCEEDED` |
| 좋아하는 것 정리 | `[" 공룡 ", "공룡", ""]` → `["공룡"]` |
| 다른 회원의 아이 조회·수정·삭제 | 모두 404 `CHILD_NOT_FOUND` |
| 삭제 | 목록에서 빠짐 / 그 아이와 연결된 동화의 child가 null이 되고 동화는 그대로 |
| 응답 traits 순서 | 요청 순서와 상관없이 `Trait` 선언 순서 |
| `GET /api/children/options` | 성향 6개, 라벨 포함 |
| `ChildProfileResolver` 다섯 경우 | 위 표대로. 특히 4번은 프로필 DB 값이 바뀌지 않았는지 다시 조회해서 확인 |

### 문서 갱신

- `CLAUDE.md` 작업 문서 목록에 TASK-03 추가
- 새 TODO(PM)이 생기면 `CLAUDE.md`의 미정 사항에도 추가

---

## 완료 조건

- [ ] `./gradlew build` 통과 (기존 테스트 포함 전부)
- [ ] DB 마이그레이션 추가 없음
- [ ] 다른 회원의 아이는 모든 API에서 404
- [ ] `ChildProfileResolver` 다섯 경우 테스트 통과
- [ ] 응답과 로그, AI로 가는 snapshot 어디에도 호칭이 섞이지 않음
- [ ] 코드에서 `Instant.now()`, `LocalDate.now()`를 직접 부르지 않고 `Clock` 사용
- [ ] 커밋은 빌드가 통과한 상태로만 (`&&` 사용)

## 패키지 위치

| 위치 | 클래스 |
|---|---|
| `domain.child.controller` | `ChildController` |
| `domain.child.service` | `ChildService`, `ChildProfileResolver` |
| `domain.child.dto` | `ChildProfileRequest`, `ChildResponse`, `DisplayNameRequest`, `ChildOptionsResponse`, `ChildSelection`, `ResolvedChild`, `ChildProfile` |
| `domain.child.entity` | `BirthYmPolicy` 추가 |
