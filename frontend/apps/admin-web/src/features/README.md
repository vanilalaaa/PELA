# features/

Mỗi feature trùng tên module backend (`identity`, `admin`, `listening`, …):

```text
features/<module>/
├─ application/      use case, port (interface) — không import React, infrastructure, presentation
├─ infrastructure/   hiện thực port (API client); mock repository ở infrastructure/mock/
└─ presentation/     page/component + hook ViewModel — không import infrastructure
```

- Composition root: `src/app/container.ts` (nơi duy nhất chọn mock hay repository thật).
- Dùng chung giữa các feature: UI ở `src/core/ui/`, hạ tầng ở `src/core/infrastructure/`.
- `src/domain/` là **tuỳ chọn**, chỉ cho logic riêng của Admin; logic dùng chung (SM-2, Star…) ở `packages/domain`.
- Quy tắc đầy đủ: [`../../AGENTS.md`](../../AGENTS.md). Luật ranh giới: `frontend/.dependency-cruiser.cjs`.
