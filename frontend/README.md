# RetailLens — Frontend (React + Vite)

매장 영상 업로드 → 분석 진행 표시 → 통계·차트·heatmap 시각화 대시보드.

## 기술 스택

| 영역      | 도구               |
| --------- | ------------------ |
| Framework | React 19 + Vite    |
| Charts    | Recharts (Pie/Bar) |
| Heatmap   | CSS Grid 직접 렌더 |

## 주요 기능

- mp4 업로드 (multipart) → `POST /jobs`
- Job 진행 polling (3초 간격, `GET /jobs/{id}`)
- 완료 시 통계 6종 카드 + 구매/미구매 파이 + 연령 분포 바 + 동선 heatmap

## 환경변수

| Key              | 설명                                                   |
| ---------------- | ------------------------------------------------------ |
| `VITE_API_URL` | backend 주소 (로컬: localhost:8080 / 배포: Render URL) |

> `VITE_API_URL`은 Vite 빌드 타임에 주입되므로, Vercel에서 환경변수 추가/변경 후 반드시 Redeploy 해야 반영됨.

## 실행

```bash
npm install
npm run dev      # http://localhost:5173
```

## 배포

Vercel (Root Directory: `frontend`, env: `VITE_API_URL`).
배포 URL: https://retaillens-xxx.vercel.app
