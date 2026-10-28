# features/

Mỗi feature trùng tên module backend (`srs`, `writing`, `listening`, …):

```text
features/<module>/
├─ application/      use case, port (interface) — không import React, infrastructure, presentation
├─ infrastructure/   hiện thực port (API client, WatermelonDB, …)
└─ presentation/     screen/component + ViewModel hook — không import infrastructure
```

- Hạ tầng dùng chung giữa các feature: `src/core/infrastructure/`.
- UI Kit dùng chung (theme token + component Button/Card/Input/...): `src/core/ui/`
  — *(thêm mới, chưa có trong tài liệu gốc; không phải infrastructure nên presentation của
  feature được import thoải mái — xem `.dependency-cruiser.cjs`, rule `presentation-no-infrastructure`
  chỉ chặn `core/infrastructure` và `features/*/infrastructure`, không chặn `core/ui`)*.
- `src/domain/` chỉ chứa logic riêng của app; logic dùng chung (SM-2, Star…) ở `packages/domain`.
- Luật ranh giới: `frontend/.dependency-cruiser.cjs`.
