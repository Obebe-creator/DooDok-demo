import type { Metadata } from "next";
import { notFound } from "next/navigation";
import {
  Badge,
  Button,
  CONDITION_GRADES,
  ConditionBadge,
  conditionGrades,
  Container,
  Eyebrow,
  TextField,
} from "@/components/ui";

export const metadata: Metadata = { title: "디자인 토큰 · 두독", robots: { index: false } };

const colors = [
  ["background", "bg-background", "#FBF5E9"],
  ["surface", "bg-surface", "#FFFDF8"],
  ["line", "bg-line", "#EADCC2"],
  ["foreground", "bg-foreground", "#3A2D20"],
  ["muted", "bg-muted", "#75614C"],
  ["brand", "bg-brand", "#5B4632"],
  ["cream", "bg-cream", "#F4D9A0"],
  ["cream-soft", "bg-cream-soft", "#F6E7C6"],
  ["accent", "bg-accent", "#C8792C"],
  ["accent-strong", "bg-accent-strong", "#A85F1C"],
  ["danger", "bg-danger", "#9F2F1F"],
] as const;

/** 개발용 디자인 토큰·컴포넌트 확인 화면. 운영 빌드에서는 404를 반환한다. */
export default function DesignPage() {
  if (process.env.NODE_ENV === "production") notFound();

  return (
    <main>
      <Container className="grid gap-14 py-14">
        <header className="grid gap-3">
          <Eyebrow>Design Tokens</Eyebrow>
          <h1 className="text-4xl font-light">시안 C · 오후의 햇살</h1>
          <p className="text-muted">globals.css의 토큰과 components/ui 컴포넌트를 한눈에 확인한다.</p>
        </header>

        <section className="grid gap-4">
          <h2 className="text-2xl">색</h2>
          <ul className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-6">
            {colors.map(([name, className, hex]) => (
              <li key={name} className="grid gap-2">
                <span className={`h-16 rounded-xs border border-line ${className}`} />
                <span className="text-sm font-medium">{name}</span>
                <span className="text-xs text-muted">{hex}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="grid gap-4">
          <h2 className="text-2xl">글꼴</h2>
          <p className="text-5xl font-light leading-tight">오늘 덮은 책이, 누군가의 첫 페이지로.</p>
          <p className="text-2xl font-bold">지금, 이웃의 책장에서</p>
          <p className="text-lg leading-relaxed">“알을 깨고 나오는 일은 한 번의 사건이 아니라, 매일 조금씩 해내는 일이었다.”</p>
          <p>본문 HS활공굴림 16px — 동네 이웃과 중고책을 나누고, 읽은 자리에 짧은 독후감을 남겨 두세요.</p>
          <p className="text-sm text-muted">보조 텍스트 14px · muted</p>
          <ul className="grid gap-1 text-lg">
            <li className="font-light">Light 300 · 읽은 책은 다음 독자에게</li>
            <li className="font-[350]">SemiLight 350 · 읽은 책은 다음 독자에게</li>
            <li className="font-normal">Regular 400 · 읽은 책은 다음 독자에게</li>
            <li className="font-medium">Medium 500 · 읽은 책은 다음 독자에게</li>
            <li className="font-bold">Bold 700 · 읽은 책은 다음 독자에게</li>
          </ul>
        </section>

        <section className="grid gap-4">
          <h2 className="text-2xl">버튼</h2>
          <div className="flex flex-wrap items-center gap-3">
            <Button>책 판매 시작하기</Button>
            <Button variant="accent">이 책 판매하기</Button>
            <Button variant="outline">매물 둘러보기</Button>
            <Button variant="ghost">취소</Button>
            <Button variant="link">독서 리포트 전체 보기</Button>
            <Button disabled>비활성</Button>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button size="sm">small</Button>
            <Button size="md">medium</Button>
            <Button size="lg">large</Button>
          </div>
          <div className="flex flex-wrap items-center gap-3 bg-brand p-6">
            <Eyebrow tone="cream">On brand</Eyebrow>
          </div>
        </section>

        <section className="grid gap-4">
          <h2 className="text-2xl">뱃지 · 책 상태 등급</h2>
          <div className="flex flex-wrap items-center gap-2">
            <Badge>소설 · 한국소설</Badge>
            <Badge tone="brand">전체</Badge>
            <Badge tone="accent">NEW</Badge>
          </div>
          <ul className="grid gap-2">
            {CONDITION_GRADES.map((grade) => (
              <li key={grade} className="flex items-center gap-3 text-sm">
                <ConditionBadge grade={grade} />
                <span className="text-muted">{grade} · {conditionGrades[grade].description}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="grid max-w-md gap-4">
          <h2 className="text-2xl">입력</h2>
          <TextField label="이메일" type="email" placeholder="reader@doodok.xyz" required={false} />
          <TextField label="닉네임" hint="한글, 영문, 숫자, 밑줄로 2~12자" required={false} />
          <TextField label="비활성" disabled defaultValue="수정할 수 없음" required={false} />
        </section>
      </Container>
    </main>
  );
}
